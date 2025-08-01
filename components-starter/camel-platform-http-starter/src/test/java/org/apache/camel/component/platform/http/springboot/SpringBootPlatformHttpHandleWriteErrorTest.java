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

import jakarta.servlet.http.HttpServletResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.component.platform.http.spi.PlatformHttpEngine;
import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;

@EnableAutoConfiguration(exclude = {OAuth2ClientAutoConfiguration.class, SecurityAutoConfiguration.class})
@CamelSpringBootTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = { CamelAutoConfiguration.class,
        SpringBootPlatformHttpHandleWriteErrorTest.class, SpringBootPlatformHttpHandleWriteErrorTest.TestConfiguration.class,
        PlatformHttpComponentAutoConfiguration.class, SpringBootPlatformHttpAutoConfiguration.class })
public class SpringBootPlatformHttpHandleWriteErrorTest extends PlatformHttpBase {

    private static final String postRouteId = "SpringBootPlatformHttpHandleWriteErrorTest_mypost";
    private static final String getRouteId = "SpringBootPlatformHttpHandleWriteErrorTest_myget";

    @Test
    @Override
    public void testGet() throws Exception {
        MockEndpoint me = camelContext.getEndpoint("mock:failure", MockEndpoint.class);
        me.expectedMessageCount(0);

        super.testGet();

        me.assertIsSatisfied();
    }

    @Test
    @Override
    public void testPost() throws Exception {
        MockEndpoint me = camelContext.getEndpoint("mock:failure", MockEndpoint.class);
        me.expectedMessageCount(1);

        waitUntilRouteIsStarted(1, getPostRouteId());
        Assertions.assertThat(restTemplate.postForEntity("/mypost", "test", String.class).getStatusCode().value()).isEqualTo(500);

        me.assertIsSatisfied();
    }

    // *************************************
    // Config
    // *************************************
    @Configuration
    public static class TestConfiguration {

        @Bean(name = "platform-http-engine")
        public PlatformHttpEngine myHttpEngine(Environment env) {
            int port = Integer.parseInt(env.getProperty("server.port", "8080"));
            return new MyEngine(port);
        }

        @Bean
        public RouteBuilder servletPlatformHttpRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    onCompletion().onFailureOnly().to("log:failure").to("mock:failure");

                    from("platform-http:/myget").id(getRouteId).setBody().constant("get");
                    from("platform-http:/mypost?handleWriteResponseError=true").id(postRouteId).transform().body(String.class, b -> b.toUpperCase());
                }
            };
        }
    }

    @Override
    protected String getPostRouteId() {
        return postRouteId;
    }

    @Override
    protected String getGetRouteId() {
        return getRouteId;
    }

    private static class MyErrorBinding extends DefaultHttpBinding {

        @Override
        public void writeResponse(Exchange exchange, HttpServletResponse response) throws IOException {
            // force an exception during writing response to simulate error at that point
            String uri = exchange.getMessage().getHeader(Exchange.HTTP_URI, String.class);
            if ("/mypost".equals(uri)) {
                throw new IOException("Forced error");
            } else {
                super.writeResponse(exchange, response);
            }
        }
    }

    private static class MyEngine extends SpringBootPlatformHttpEngine {

        public MyEngine(int port) {
            super(port);
        }

        @Override
        public PlatformHttpConsumer createConsumer(PlatformHttpEndpoint endpoint, Processor processor) {
            SpringBootPlatformHttpConsumer answer = new SpringBootPlatformHttpConsumer(endpoint, processor);
            answer.setBinding(new MyErrorBinding());
            return answer;
        }
    }
}
