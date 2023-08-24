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
package org.apache.camel.dataformat.swift.mt.springboot;

import org.apache.camel.spring.boot.DataFormatConfigurationPropertiesCommon;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Encode and decode SWIFT MT messages.
 * 
 * Generated by camel-package-maven-plugin - do not edit this file!
 */
@ConfigurationProperties(prefix = "camel.dataformat.swift-mt")
public class SwiftMtDataFormatConfiguration
        extends
            DataFormatConfigurationPropertiesCommon {

    /**
     * Whether to enable auto configuration of the swiftMt data format. This is
     * enabled by default.
     */
    private Boolean enabled;
    /**
     * The flag indicating that messages must be marshalled in a JSON format.
     */
    private Boolean writeInJson = false;

    public Boolean getWriteInJson() {
        return writeInJson;
    }

    public void setWriteInJson(Boolean writeInJson) {
        this.writeInJson = writeInJson;
    }
}