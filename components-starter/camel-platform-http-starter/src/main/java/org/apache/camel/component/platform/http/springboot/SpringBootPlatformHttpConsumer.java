/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.platform.http.springboot;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.cookie.CookieConfiguration;
import org.apache.camel.component.platform.http.cookie.CookieHandler;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ResponseBody;

public class SpringBootPlatformHttpConsumer extends DefaultConsumer implements PlatformHttpConsumer, Suspendable, SuspendableService {

    private static final Logger LOG = LoggerFactory.getLogger(SpringBootPlatformHttpConsumer.class);

    private HttpBinding binding;
    private final boolean handleWriteResponseError;
    private CookieConfiguration cookieConfiguration;
    private Executor executor;

    public SpringBootPlatformHttpConsumer(PlatformHttpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.binding = new SpringBootPlatformHttpBinding();
        this.binding.setHeaderFilterStrategy(endpoint.getHeaderFilterStrategy());
        this.binding.setMuteException(endpoint.isMuteException());
        this.binding.setFileNameExtWhitelist(endpoint.getFileNameExtWhitelist());
        this.binding.setUseReaderForPayload(!endpoint.isUseStreaming());
        this.handleWriteResponseError = endpoint.isHandleWriteResponseError();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public SpringBootPlatformHttpConsumer(PlatformHttpEndpoint endpoint, Processor processor, Executor executor) {
        this(endpoint, processor);
        this.executor = executor;
    }

    /**
     * Used for testing purposes
     */
    void setBinding(HttpBinding binding) {
        this.binding = binding;
    }

    @Override
    public PlatformHttpEndpoint getEndpoint() {
        return (PlatformHttpEndpoint) super.getEndpoint();
    }

    /**
     * This method is invoked by Spring Boot when invoking Camel via platform-http.
     *
     * The method is already running asynchronously via AsyncExecutionInterceptor.
     *
     * Returns an empty CompletableFuture as per documentation https://spring.io/guides/gs/async-method
     */
    @ResponseBody
    public CompletableFuture<Void> service(HttpServletRequest request, HttpServletResponse response) {
        return CompletableFuture.runAsync(() -> {
            LOG.trace("Service: {}", request);
            try {
                handleService(request, response);
            } catch (Exception e) {
                // do not leak exception back to caller
                LOG.warn("Error handling request due to: {}", e.getMessage(), e);
                try {
                    if (!response.isCommitted()) {
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }
                } catch (Exception e1) {
                    // ignore
                }
            }
        }, executor);
    }

    protected void handleService(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (isSuspended()) {
            LOG.debug("Consumer suspended, cannot service request: {}", request);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        Exchange exchange = createExchange(true);
        exchange.setPattern(ExchangePattern.InOut);
        HttpHelper.setCharsetFromContentType(request.getContentType(), exchange);
        PlatformHttpMessage msg = new PlatformHttpMessage(request, response, exchange,binding, false);
        exchange.setIn(msg);
        msg.init(exchange, binding, request, response);
        String contextPath = getEndpoint().getPath();
        exchange.getIn().setHeader(SpringBootPlatformHttpConstants.CONTEXT_PATH, contextPath);
        if (getEndpoint().isUseCookieHandler()) {
            cookieConfiguration = getEndpoint().getCookieConfiguration();
            exchange.setProperty(Exchange.COOKIE_HANDLER, new SpringBootCookieHandler(request, response));
        }

        // we want to handle the UoW
        try {
            createUoW(exchange);
        } catch (Exception e) {
            throw new ServletException(e);
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing request for exchangeId: {}", exchange.getExchangeId());
        }
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            afterProcess(response, exchange);
        }
    }

    protected void afterProcess(HttpServletResponse response, Exchange exchange) throws Exception {
        boolean writeFailure = false;
        try {
            // now lets output to the res
            if (LOG.isTraceEnabled()) {
                LOG.trace("Writing res for exchangeId: {}", exchange.getExchangeId());
            }
            binding.writeResponse(exchange, response);
        } catch (Exception e) {
            writeFailure = true;
            handleFailure(exchange, e);
        } finally {
            doneUoW(exchange);
            releaseExchange(exchange, false);
        }
        try {
            if (writeFailure && !response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            // ignore
        }

    }

    private void handleFailure(Exchange exchange, Throwable failure) {
        getExceptionHandler().handleException(
                "Failed writing HTTP response url: " + getEndpoint().getPath() + " due to: " + failure.getMessage(),
                failure);
        if (handleWriteResponseError) {
            Exception existing = exchange.getException();
            if (existing != null) {
                failure.addSuppressed(existing);
            }
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, failure);
            exchange.setException(failure);
        }
    }

    class SpringBootCookieHandler implements CookieHandler {

        private final HttpServletRequest request;
        private final HttpServletResponse response;

        public SpringBootCookieHandler(HttpServletRequest request, HttpServletResponse response) {
            this.request = request;
            this.response = response;
        }

        @Override
        public void addCookie(String cookieName, String cookieValue) {
            Cookie cookie = new Cookie(cookieName, cookieValue);
            cookie.setPath(cookieConfiguration.getCookiePath());
            cookie.setDomain(cookieConfiguration.getCookieDomain());
            cookie.setSecure(cookieConfiguration.isCookieSecure());
            cookie.setHttpOnly(cookieConfiguration.isCookieHttpOnly());
            cookie.setAttribute("SameSite", cookieConfiguration.getCookieSameSite().getValue());
            if (cookieConfiguration.getCookieMaxAge() != null) {
                cookie.setMaxAge(Math.toIntExact(cookieConfiguration.getCookieMaxAge()));
            }
            response.addCookie(cookie);
        }

        @Override
        public String removeCookie(String cookieName) {
            Cookie cookieToRemove = new Cookie(cookieName, null);
            cookieToRemove.setPath(cookieConfiguration.getCookiePath());
            // set max age to 0 to delete the cookie
            cookieToRemove.setMaxAge(0);
            response.addCookie(cookieToRemove);
            return cookieName;
        }

        @Override
        public String getCookieValue(@CookieValue String cookieName) {
            Cookie[] cookie = request.getCookies();
            // ensure cookies are not null
            if (cookie != null) {
                for (Cookie c : cookie) {
                    if (c.getName().equals(cookieName)) {
                        return c.getValue();
                    }
                }
            }
            return "Cookie not found";
        }
    }
}


