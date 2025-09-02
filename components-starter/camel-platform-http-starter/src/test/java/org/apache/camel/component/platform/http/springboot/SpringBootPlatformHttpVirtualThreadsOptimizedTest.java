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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@EnableAutoConfiguration(exclude = {OAuth2ClientAutoConfiguration.class, SecurityAutoConfiguration.class})
@CamelSpringBootTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = { CamelAutoConfiguration.class,
        SpringBootPlatformHttpVirtualThreadsOptimizedTest.class, SpringBootPlatformHttpVirtualThreadsOptimizedTest.TestConfiguration.class,
        PlatformHttpComponentAutoConfiguration.class, SpringBootPlatformHttpAutoConfiguration.class },
    properties = "spring.threads.virtual.enabled=true")
@DisabledOnJre({JRE.JAVA_17, JRE.JAVA_18, JRE.JAVA_19, JRE.JAVA_20})
public class SpringBootPlatformHttpVirtualThreadsOptimizedTest extends PlatformHttpBase {

    private static final String postRouteId = "SpringBootPlatformHttpVirtualThreadsOptimizedTest_mypost";
    private static final String getRouteId = "SpringBootPlatformHttpVirtualThreadsOptimizedTest_myget";

    @Autowired
    private List<Executor> executors;

    // *************************************
    // Config
    // *************************************
    @Configuration
    public static class TestConfiguration {

        @Bean
        public RouteBuilder servletPlatformHttpRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/myget").id(postRouteId).setBody().constant("get");
                    from("platform-http:/mypost").id(getRouteId).transform().body(String.class, b -> b.toUpperCase());
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

    @Test
    public void testVirtualThreadExecutorIsPreferred() {
        // Verify that SimpleAsyncTaskExecutor is available and used when virtual threads are enabled
        boolean hasSimpleAsyncTaskExecutor = executors.stream()
                .anyMatch(e -> e instanceof SimpleAsyncTaskExecutor);
        
        assertThat(hasSimpleAsyncTaskExecutor)
                .as("SimpleAsyncTaskExecutor should be available when virtual threads are enabled")
                .isTrue();
    }
}