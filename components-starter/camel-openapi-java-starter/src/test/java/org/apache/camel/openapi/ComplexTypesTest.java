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
package org.apache.camel.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.openapi.model.SampleComplexRequestType;
import org.apache.camel.openapi.model.SampleComplexResponseType;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;

import org.junit.jupiter.api.Test;

import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.OpenAPI;

@DirtiesContext
@CamelSpringBootTest
@SpringBootTest(classes = { CamelAutoConfiguration.class, ComplexTypesTest.class,
        ComplexTypesTest.TestConfiguration.class, DummyRestConsumerFactory.class })
public class ComplexTypesTest {
    private static final Logger LOG = LoggerFactory.getLogger(ComplexTypesTest.class);

    @SuppressWarnings("unused")
    @BindToRegistry("dummy-rest")
    private final DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Autowired
    CamelContext context;

    // *************************************
    // Config
    // *************************************

    @Configuration
    public class TestConfiguration {

        @Bean
        public RouteBuilder routeBuilder() {
            return new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    rest().securityDefinitions().oauth2("global")
                            .accessCode("https://AUTHORIZATION_URL", "https://TOKEN_URL")
                            .withScope("groups", "Required scopes for Camel REST APIs");

                    rest().post("/complexRequest").description("Demo complex request type")
                            .type(SampleComplexRequestType.class).consumes("application/json").produces("text/plain")
                            .bindingMode(RestBindingMode.json).responseMessage().code(200)
                            .message("Receives a complex object as parameter").endResponseMessage()
                            .outType(SampleComplexResponseType.InnerClass.class).to("direct:request");
                    from("direct:request").routeId("complex request type").log("/complex request invoked");

                    rest().get("/complexResponse").description("Demo complex response type")
                            .type(SampleComplexRequestType.InnerClass.class).consumes("application/json")
                            .outType(SampleComplexResponseType.class).produces("application/json")
                            .bindingMode(RestBindingMode.json).responseMessage().code(200)
                            .message("Returns a complex object").endResponseMessage().to("direct:response");

                    from("direct:response").routeId("complex response type").log("/complex invoked")
                            .setBody(constant(new SampleComplexResponseType()));
                }
            };
        }
    }

    @Test
    public void testV3SchemaForComplexTypesRequest() throws Exception {
        checkSchemaGeneration("/complexRequest", "3.0", "V3SchemaForComplexTypesRequest.json");
    }

    @Test
    public void testV3SchemaForComplexTypesResponse() throws Exception {
        checkSchemaGeneration("/complexResponse", "3.0", "V3SchemaForComplexTypesResponse.json");
    }

    private void checkSchemaGeneration(String uri, String apiVersion, String schemaResource) throws Exception {
        BeanConfig config = getBeanConfig(apiVersion);

        List<RestDefinition> rests = ((ModelCamelContext) context).getRestDefinitions().stream()
                // So we get the security schema and the route schema
                .filter(def -> def.getVerbs().isEmpty() || def.getVerbs().get(0).getPath().equals(uri))
                .collect(Collectors.toList());

        RestOpenApiReader reader = new RestOpenApiReader();
        OpenAPI openApi = reader.read(context, rests, config, context.getName(), new DefaultClassResolver());
        assertNotNull(openApi);
        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);

        LOG.info(json);

        json = generify(json);

        InputStream is = getClass().getClassLoader().getResourceAsStream("org/apache/camel/openapi/" + schemaResource);
        assertNotNull(is);
        String expected = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines()
                .collect(Collectors.joining("\n"));
        is.close();

        JSONAssert.assertEquals(expected, json, JSONCompareMode.STRICT);
    }

    private BeanConfig getBeanConfig(String apiVersion) {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("https://www.apache.org/licenses/LICENSE-2.0.html");
        config.setVersion(apiVersion);
        return config;
    }

    private String generify(String input) {
        input = input.replaceAll("\"openapi\" : \"3\\..*\",", "\"openapi\" : \"3.x\",");
        input = input.replaceAll("\"swagger\" : \"2\\..*\",", "\"swagger\" : \"2.x\",");
        input = input.replaceAll("\"operationId\" : \"verb.*\",", "\"operationId\" : \"verb\",");
        input = input.replaceAll("\"x-camelContextId\" : \"camel.*\"", "\"x-camelContextId\" : \"camel\"");
        return input;
    }
}
