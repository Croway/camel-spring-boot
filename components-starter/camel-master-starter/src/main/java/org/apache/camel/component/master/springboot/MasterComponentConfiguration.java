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
package org.apache.camel.component.master.springboot;

import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.cluster.CamelClusterService.Selector;
import org.apache.camel.spring.boot.ComponentConfigurationPropertiesCommon;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Have only a single consumer in a cluster consuming from a given endpoint;
 * with automatic failover if the JVM dies.
 * 
 * Generated by camel-package-maven-plugin - do not edit this file!
 */
@ConfigurationProperties(prefix = "camel.component.master")
public class MasterComponentConfiguration
        extends
            ComponentConfigurationPropertiesCommon {

    /**
     * Whether to enable auto configuration of the master component. This is
     * enabled by default.
     */
    private Boolean enabled;
    /**
     * Allows for bridging the consumer to the Camel routing Error Handler,
     * which mean any exceptions (if possible) occurred while the Camel consumer
     * is trying to pickup incoming messages, or the likes, will now be
     * processed as a message and handled by the routing Error Handler.
     * Important: This is only possible if the 3rd party component allows Camel
     * to be alerted if an exception was thrown. Some components handle this
     * internally only, and therefore bridgeErrorHandler is not possible. In
     * other situations we may improve the Camel component to hook into the 3rd
     * party component and make this possible for future releases. By default
     * the consumer will use the org.apache.camel.spi.ExceptionHandler to deal
     * with exceptions, that will be logged at WARN or ERROR level and ignored.
     */
    private Boolean bridgeErrorHandler = false;
    /**
     * Whether autowiring is enabled. This is used for automatic autowiring
     * options (the option must be marked as autowired) by looking up in the
     * registry to find if there is a single instance of matching type, which
     * then gets configured on the component. This can be used for automatic
     * configuring JDBC data sources, JMS connection factories, AWS Clients,
     * etc.
     */
    private Boolean autowiredEnabled = true;
    /**
     * When the master becomes leader then backoff is in use to repeat starting
     * the consumer until the consumer is successfully started or max attempts
     * reached. This option is the delay in millis between start attempts.
     */
    private Long backOffDelay;
    /**
     * When the master becomes leader then backoff is in use to repeat starting
     * the consumer until the consumer is successfully started or max attempts
     * reached. This option is the maximum number of attempts to try.
     */
    private Integer backOffMaxAttempts;
    /**
     * Inject the service to use. The option is a
     * org.apache.camel.cluster.CamelClusterService type.
     */
    private CamelClusterService service;
    /**
     * Inject the service selector used to lookup the CamelClusterService to
     * use. The option is a
     * org.apache.camel.cluster.CamelClusterService.Selector type.
     */
    private Selector serviceSelector;

    public Boolean getBridgeErrorHandler() {
        return bridgeErrorHandler;
    }

    public void setBridgeErrorHandler(Boolean bridgeErrorHandler) {
        this.bridgeErrorHandler = bridgeErrorHandler;
    }

    public Boolean getAutowiredEnabled() {
        return autowiredEnabled;
    }

    public void setAutowiredEnabled(Boolean autowiredEnabled) {
        this.autowiredEnabled = autowiredEnabled;
    }

    public Long getBackOffDelay() {
        return backOffDelay;
    }

    public void setBackOffDelay(Long backOffDelay) {
        this.backOffDelay = backOffDelay;
    }

    public Integer getBackOffMaxAttempts() {
        return backOffMaxAttempts;
    }

    public void setBackOffMaxAttempts(Integer backOffMaxAttempts) {
        this.backOffMaxAttempts = backOffMaxAttempts;
    }

    public CamelClusterService getService() {
        return service;
    }

    public void setService(CamelClusterService service) {
        this.service = service;
    }

    public Selector getServiceSelector() {
        return serviceSelector;
    }

    public void setServiceSelector(Selector serviceSelector) {
        this.serviceSelector = serviceSelector;
    }
}