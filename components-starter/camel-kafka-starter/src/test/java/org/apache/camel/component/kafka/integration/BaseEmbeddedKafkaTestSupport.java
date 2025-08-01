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
package org.apache.camel.component.kafka.integration;

import java.util.Properties;
import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public abstract class BaseEmbeddedKafkaTestSupport {

    @RegisterExtension
    public static KafkaService service = KafkaServiceFactory.createService();

    @Autowired
    protected CamelContext context;

    protected static AdminClient kafkaAdminClient;

    private static final Logger LOG = LoggerFactory.getLogger(BaseEmbeddedKafkaTestSupport.class);

    @BeforeAll
    public static void beforeClass() {
        LOG.info("### Embedded Kafka cluster broker list: {}", service.getBootstrapServers());
        System.setProperty("bootstrapServers", service.getBootstrapServers());
    }

    @BeforeEach
    public void setKafkaAdminClient() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = createAdminClient();
        }
    }

    protected Properties getDefaultProperties() {
        LOG.info("Connecting to Kafka {}", service.getBootstrapServers());

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return props;
    }

    protected static String getBootstrapServers() {
        return service.getBootstrapServers();
    }

    private static AdminClient createAdminClient() {
        final Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());

        return KafkaAdminClient.create(properties);
    }

    @Configuration
    public class DefaulKafkaComponent {
        @Bean
        CamelContextConfiguration contextConfiguration() {
            return new CamelContextConfiguration() {
                @Override
                public void beforeApplicationStart(CamelContext context) {
                    context.getPropertiesComponent().setLocation("ref:prop");

                    KafkaComponent kafka = new KafkaComponent(context);
                    kafka.init();
                    kafka.getConfiguration().setBrokers(BaseEmbeddedKafkaTestSupport.service.getBootstrapServers());
                    kafka.getConfiguration().setRecordMetadata(true);

                    context.addComponent("kafka", kafka);
                }

                @Override
                public void afterApplicationStart(CamelContext camelContext) {
                    // do nothing here
                }
            };
        }
    }
}
