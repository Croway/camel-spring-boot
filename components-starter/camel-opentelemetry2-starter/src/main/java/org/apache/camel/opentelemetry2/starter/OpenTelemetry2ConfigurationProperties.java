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
package org.apache.camel.opentelemetry2.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.opentelemetry2")
public class OpenTelemetry2ConfigurationProperties {

    /**
     * Sets exclude pattern(s) that will disable tracing for Camel messages that matches the pattern. Multiple patterns
     * can be separated by comma.
     */
    private String excludePatterns;

    /**
     * Setting this to true will create new OpenTelemetry Spans for each Camel Processors. Use the excludePattern
     * property to filter out Processors.
     */
    private Boolean traceProcessors;

    public Boolean getTraceProcessors() {
        return traceProcessors;
    }

    public void setTraceProcessors(Boolean traceProcessors) {
        this.traceProcessors = traceProcessors;
    }

    public String getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(String excludePatterns) {
        this.excludePatterns = excludePatterns;
    }
}
