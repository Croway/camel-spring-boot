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
package org.apache.camel.component.jsonpath.springboot.test;

import java.io.File;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;

@DirtiesContext
@CamelSpringBootTest
@SpringBootTest(classes = { CamelAutoConfiguration.class, SpringJsonPathTransformResultTypeTest.class }, properties = {
        "camel.main.routes-include-pattern=file:src/test/resources/routes/SpringJsonPathTransformResultTypeTest.xml" }
)
public class SpringJsonPathTransformResultTypeTest {

    @Autowired
    ProducerTemplate template;

    @EndpointInject("mock:price")
    MockEndpoint mock;

    @Test
    public void testPriceResultType() throws Exception {
        mock.expectedMessageCount(4);
        mock.message(0).body().isInstanceOf(Float.class);
        mock.message(1).body().isInstanceOf(Double.class);
        mock.message(2).body().isInstanceOf(Float.class);
        mock.message(3).body().isInstanceOf(Double.class);

        template.sendBody("direct:start", new File("src/test/resources/books.json"));
        template.sendBody("direct:second", new File("src/test/resources/books.json"));
        template.sendBody("direct:start", new File("src/test/resources/books.json"));
        template.sendBody("direct:second", new File("src/test/resources/books.json"));

        mock.assertIsSatisfied();

    }
}
