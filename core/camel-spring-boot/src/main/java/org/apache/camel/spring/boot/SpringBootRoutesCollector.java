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
package org.apache.camel.spring.boot;

import java.io.FileNotFoundException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.LambdaRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.DefaultRoutesCollector;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.ObjectHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

/**
 * Spring Boot {@link org.apache.camel.main.RoutesCollector}.
 */
public class SpringBootRoutesCollector extends DefaultRoutesCollector {

    private final ApplicationContext applicationContext;

    public SpringBootRoutesCollector(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public List<RoutesBuilder> collectRoutesFromRegistry(final CamelContext camelContext, final String excludePattern, final String includePattern) {
        final List<RoutesBuilder> routes = new ArrayList<>();

        Set<LambdaRouteBuilder> lrbs = camelContext.getRegistry().findByType(LambdaRouteBuilder.class);
        for (LambdaRouteBuilder lrb : lrbs) {
            RouteBuilder rb = new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    lrb.accept(this);
                }
            };
            routes.add(rb);
        }

        final AntPathMatcher matcher = new AntPathMatcher();
        for (RoutesBuilder routesBuilder : applicationContext.getBeansOfType(RoutesBuilder.class, true, true).values()) {
            // filter out abstract classes
            boolean abs = Modifier.isAbstract(routesBuilder.getClass().getModifiers());
            if (!abs) {
                String name = routesBuilder.getClass().getName();
                // make name as path so we can use ant path matcher
                name = name.replace('.', '/');

                boolean match = !"false".equals(includePattern);
                // exclude take precedence over include
                if (match && ObjectHelper.isNotEmpty(excludePattern)) {
                    // there may be multiple separated by comma
                    String[] parts = excludePattern.split(",");
                    for (String part : parts) {
                        // must negate when excluding, and hence !
                        match = !matcher.match(part, name);
                        log.trace("Java RoutesBuilder: {} exclude filter: {} -> {}", name, part, match);
                        if (!match) {
                            break;
                        }
                    }
                }
                // special support for testing with @ExcludeRoutes annotation with camel-test-spring
                String sysExcludePattern = System.getProperty("CamelTestSpringExcludeRoutes");
                // exclude take precedence over include
                if (match && ObjectHelper.isNotEmpty(sysExcludePattern)) {
                    // this property is a comma separated list of FQN class names, so we need to make
                    // name as path so we can use ant patch matcher
                    sysExcludePattern = sysExcludePattern.replace('.', '/');
                    // there may be multiple separated by comma
                    String[] parts = sysExcludePattern.split(",");
                    for (String part : parts) {
                        // must negate when excluding, and hence !
                        match = !matcher.match(part, name);
                        log.trace("Java RoutesBuilder: {} exclude filter: {} -> {}", name, part, match);
                        if (!match) {
                            break;
                        }
                    }
                }
                if (match && ObjectHelper.isNotEmpty(includePattern)) {
                    // there may be multiple separated by comma
                    String[] parts = includePattern.split(",");
                    for (String part : parts) {
                        match = matcher.match(part, name);
                        log.trace("Java RoutesBuilder: {} include filter: {} -> {}", name, part, match);
                        if (match) {
                            break;
                        }
                    }
                }
                log.debug("Java RoutesBuilder: {} accepted by include/exclude filter: {}", name, match);
                if (match) {
                    routes.add(routesBuilder);
                }
            }
        }

        return routes;
    }

    @Override
    public List<RoutesDefinition> collectXmlRoutesFromDirectory(CamelContext camelContext, String directory) {
        List<RoutesDefinition> answer = new ArrayList<>();

        String[] parts = directory.split(",");
        for (String part : parts) {
            log.debug("Loading additional Camel XML routes from: {}", part);
            try {
                Resource[] xmlRoutes = applicationContext.getResources(part);
                for (Resource xmlRoute : xmlRoutes) {
                    log.debug("Found XML route: {}", xmlRoute);
                    ExtendedCamelContext extendedCamelContext = camelContext.adapt(ExtendedCamelContext.class);
                    XMLRoutesDefinitionLoader xmlLoader = extendedCamelContext.getXMLRoutesDefinitionLoader();
                    RoutesDefinition routes = (RoutesDefinition) xmlLoader.loadRoutesDefinition(camelContext, xmlRoute.getInputStream());
                    if (routes != null) {
                        answer.add(routes);
                    }
                }
            } catch (FileNotFoundException e) {
                log.debug("No XML routes found in {}. Skipping XML routes detection.", part);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        return answer;
    }

    @Override
    public List<RouteTemplatesDefinition> collectXmlRouteTemplatesFromDirectory(CamelContext camelContext, String directory) throws Exception {
        List<RouteTemplatesDefinition> answer = new ArrayList<>();

        String[] parts = directory.split(",");
        for (String part : parts) {
            log.debug("Loading additional Camel XML route templates from: {}", part);
            try {
                Resource[] xmlRouteTemplates = applicationContext.getResources(part);
                for (Resource xmlRoute : xmlRouteTemplates) {
                    log.debug("Found XML route template: {}", xmlRoute);
                    ExtendedCamelContext extendedCamelContext = camelContext.adapt(ExtendedCamelContext.class);
                    XMLRoutesDefinitionLoader xmlLoader = extendedCamelContext.getXMLRoutesDefinitionLoader();
                    RouteTemplatesDefinition templates = (RouteTemplatesDefinition) xmlLoader.loadRouteTemplatesDefinition(camelContext, xmlRoute.getInputStream());
                    if (templates != null) {
                        answer.add(templates);
                    }
                }
            } catch (FileNotFoundException e) {
                log.debug("No XML route templates found in {}. Skipping XML route templates detection.", part);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        return answer;
    }

    @Override
    public List<RestsDefinition> collectXmlRestsFromDirectory(CamelContext camelContext, String directory) {
        List<RestsDefinition> answer = new ArrayList<>();

        String[] parts = directory.split(",");
        for (String part : parts) {
            log.debug("Loading additional Camel XML rests from: {}", part);
            try {
                final Resource[] xmlRests = applicationContext.getResources(part);
                for (final Resource xmlRest : xmlRests) {
                    ExtendedCamelContext extendedCamelContext = camelContext.adapt(ExtendedCamelContext.class);
                    XMLRoutesDefinitionLoader xmlLoader = extendedCamelContext.getXMLRoutesDefinitionLoader();
                    RestsDefinition rests = (RestsDefinition) xmlLoader.loadRestsDefinition(camelContext, xmlRest.getInputStream());
                    if (rests != null) {
                        answer.add(rests);
                    }
                }
            } catch (FileNotFoundException e) {
                log.debug("No XML rests found in {}. Skipping XML rests detection.", part);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        return answer;
    }

}
