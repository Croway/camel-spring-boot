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
package org.apache.camel.component.quartz.springboot;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.routepolicy.quartz.CronScheduledRoutePolicy;
import org.apache.camel.spring.boot.CamelAutoConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;

@DirtiesContext
@CamelSpringBootTest
@SpringBootTest(classes = { CamelAutoConfiguration.class, RouteAutoStopFalseCronScheduledPolicyTest.class })
public class RouteAutoStopFalseCronScheduledPolicyTest {

    @Autowired
    ProducerTemplate template;

    @Autowired
    CamelContext context;

    @EndpointInject("mock:foo")
    MockEndpoint mock;

    @Test
    public void testCronPolicy() throws Exception {
        // send a message on the seda queue so we have a message to start with
        template.sendBody("seda:foo", "Hello World");

        mock.expectedMessageCount(1);

        final CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
        policy.setRouteStartTime("*/5 * * * * ?");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").autoStartup(false).routePolicy(policy).to("mock:foo");
            }
        });

        mock.assertIsSatisfied();
    }

}
