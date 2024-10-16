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
package org.apache.camel.spring.boot.xml;

import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.TypeConversionConfiguration;
import org.apache.camel.spring.xml.XmlCamelContextConfigurer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;

@Configuration(proxyBeanMethods = false)
@Import(TypeConversionConfiguration.class)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@AutoConfigureBefore(CamelAutoConfiguration.class)
public class CamelXmlAutoConfiguration {

    /**
     * Allows to do custom configuration when running XML based Camel in Spring Boot
     */
    // must be named xmlCamelContextConfigurer
    @Bean(name = "xmlCamelContextConfigurer")
    XmlCamelContextConfigurer springBootCamelContextConfigurer() {
        return new SpringBootXmlCamelContextConfigurer();
    }

}
