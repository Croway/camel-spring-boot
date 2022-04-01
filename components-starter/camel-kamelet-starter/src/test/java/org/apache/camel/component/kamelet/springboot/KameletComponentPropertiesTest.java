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
package org.apache.camel.component.kamelet.springboot;

import static org.apache.camel.util.PropertiesHelper.asProperties;

import static org.junit.jupiter.api.Assertions.fail;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kamelet.Kamelet;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseOverridePropertiesWithPropertiesComponent;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Properties;

@DirtiesContext
@CamelSpringBootTest
@TestPropertySource(locations = { "classpath:application.properties", "classpath:kamelet.test.properties" })
@SpringBootTest(
		properties = { "bodyValue", "test" },
		classes = {
				CamelAutoConfiguration.class,
				KameletComponentPropertiesTest.class
		}
)
@Disabled
public class KameletComponentPropertiesTest {
	private static final String KAMELET_COMPONENT_PROPERTY_PREFIX = "camel.component.kamelet.";

	@Autowired
	ProducerTemplate producer;

	@Autowired
	CamelContext context;

	@Test
	public void testComponentConfigurationAndConfigurationPrecedence() {
		try {
			assertThat(
					producer
							.requestBody("kamelet:setBody/test", "", String.class)).isEqualTo("from-component-route");

			assertThat(
					producer
							.requestBody("kamelet:setBody", "", String.class)).isEqualTo("from-component-template");
			// uri properties have precedence over component properties.
			assertThat(
					producer
							.requestBody("kamelet:setBody?bodyValue={{bodyValue}}", "", String.class)).isEqualTo("from-uri");
		} catch (Exception e) {
			fail(e);
		}
	}

//	@UseOverridePropertiesWithPropertiesComponent
//	public static Properties getTestProperties() {
//		return asProperties(
//				"proxy.usr", "u+sr",
//				"proxy.pwd", "p+wd",
//				"raw.proxy.usr", "RAW(u+sr)",
//				"raw.proxy.pwd", "RAW(p+wd)",
//				// component properties have precedence over global properties
//				KAMELET_COMPONENT_PROPERTY_PREFIX + "template-properties.setBody.bodyValue", "from-component-template",
//				KAMELET_COMPONENT_PROPERTY_PREFIX + "route-properties.test.bodyValue", "from-component-route",
//				Kamelet.PROPERTIES_PREFIX + "setBody.bodyValue", "from-template",
//				Kamelet.PROPERTIES_PREFIX + "setBody.test.bodyValue", "from-route");
//	}

	@Bean
	protected RoutesBuilder createRouteBuilder() {

		return new RouteBuilder() {
			@Override
			public void configure() {
				// template
				routeTemplate("setBody")
						.templateParameter("bodyValue")
						.from("kamelet:source")
						.setBody().constant("{{bodyValue}}");

				// routes
				from("direct:someId").to("kamelet:setBody/someId");

				from("direct:test").to("kamelet:setBody");
			}
		};
	}
}