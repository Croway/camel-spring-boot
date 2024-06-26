/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.springboot.cluster;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.cluster.file")
public class FileLockClusterServiceConfiguration {
    /**
     * Sets if the file cluster service should be enabled or not, default is true.
     */
    private boolean enabled = true;

    /**
     * Cluster Service ID
     */
    private String id;

    /**
     * The root path.
     */
    private String root;

    /**
     * The time to wait before starting to try to acquire lock.
     */
    private String acquireLockDelay;

    /**
     * The time to wait between attempts to try to acquire lock.
     */
    private String acquireLockInterval;

    /**
     * Custom service attributes.
     */
    private Map<String, Object> attributes;

    /**
     * Service lookup order/priority.
     */
    private Integer order;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getAcquireLockDelay() {
        return acquireLockDelay;
    }

    public void setAcquireLockDelay(String acquireLockDelay) {
        this.acquireLockDelay = acquireLockDelay;
    }

    public String getAcquireLockInterval() {
        return acquireLockInterval;
    }

    public void setAcquireLockInterval(String acquireLockInterval) {
        this.acquireLockInterval = acquireLockInterval;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }
}
