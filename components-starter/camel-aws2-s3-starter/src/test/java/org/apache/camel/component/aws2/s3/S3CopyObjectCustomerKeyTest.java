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
package org.apache.camel.component.aws2.s3;

import java.io.InputStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.utils.Md5Utils;

import javax.crypto.KeyGenerator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static software.amazon.awssdk.services.s3.model.ServerSideEncryption.AES256;

//Based on S3CopyObjectCustomerKeyIT
@DirtiesContext
@CamelSpringBootTest
@SpringBootTest(classes = { CamelAutoConfiguration.class, S3CopyObjectCustomerKeyTest.class,
        S3CopyObjectCustomerKeyTest.TestConfiguration.class })
@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Disabled on GH Action due to Docker limit")
@Disabled("Broken test")
public class S3CopyObjectCustomerKeyTest extends BaseS3 {

    byte[] secretKey = generateSecretKey();
    String b64Key = Base64.getEncoder().encodeToString(secretKey);
    String b64KeyMd5 = Md5Utils.md5AsBase64(secretKey);

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "test.txt");
                exchange.getIn().setBody("Test");
            }
        });

        template.send("direct:copyObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "test.txt");
                exchange.getIn().setHeader(AWS2S3Constants.DESTINATION_KEY, "test1.txt");
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_DESTINATION_NAME, "mycamel1");
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.copyObject);
            }
        });

        Exchange res = template.request("direct:getObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder().key("test1.txt").bucket("mycamel1")
                        .sseCustomerKey(b64Key).sseCustomerAlgorithm(AES256.name()).sseCustomerKeyMD5(b64KeyMd5)
                        .build();
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.getObject);
                exchange.getIn().setBody(getObjectRequest);
            }
        });

        Exchange res1 = template.request("direct:listObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
            }
        });

        List response = res1.getIn().getBody(List.class);

        assertEquals(1, response.size());

        assertMockEndpointsSatisfied();
    }

    protected static byte[] generateSecretKey() {
        KeyGenerator generator;
        try {
            generator = KeyGenerator.getInstance("AES");
            generator.init(256, new SecureRandom());
            return generator.generateKey().getEncoded();
        } catch (Exception e) {
            fail("Unable to generate symmetric key: " + e.getMessage());
            return null;
        }
    }

    // *************************************
    // Config
    // *************************************

    @Configuration
    public class TestConfiguration extends BaseS3.TestConfiguration {
        @Bean
        public RouteBuilder routeBuilder(S3Client s3Client) {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    String awsEndpoint = "aws2-s3://mycamel?autoCreateBucket=true&useCustomerKey=true&customerKeyId=RAW("
                            + b64Key + ")&customerKeyMD5=RAW(" + b64KeyMd5 + ")&customerAlgorithm=" + AES256.name();
                    String awsEndpoint1 = "aws2-s3://mycamel1?autoCreateBucket=true&pojoRequest=true";
                    String awsEndpoint2 = "aws2-s3://mycamel1?autoCreateBucket=true";

                    from("direct:putObject").setHeader(AWS2S3Constants.KEY, constant("test.txt"))
                            .setBody(constant("Test")).to(awsEndpoint);

                    from("direct:copyObject").to(awsEndpoint);

                    from("direct:listObject").to(awsEndpoint2);

                    from("direct:getObject").to(awsEndpoint1).to("mock:result");
                }
            };
        }
    }
}
