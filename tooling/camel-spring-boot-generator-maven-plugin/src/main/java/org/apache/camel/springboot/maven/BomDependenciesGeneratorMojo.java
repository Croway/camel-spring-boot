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
package org.apache.camel.springboot.maven;

import org.apache.camel.maven.bom.generator.DependencyMatcher;
import org.apache.camel.maven.bom.generator.DependencySet;
import org.apache.camel.maven.bom.generator.ExternalBomConflictCheck;
import org.apache.camel.maven.bom.generator.ExternalBomConflictCheckSet;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generate BOM by flattening the current project's dependency management section and applying exclusions.
 */
@Mojo(name = "generate-dependencies-bom", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class BomDependenciesGeneratorMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    /**
     * The maven session.
     */
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    /**
     * The source pom template file.
     */
    @Parameter(defaultValue = "${basedir}/pom.xml")
    protected File sourcePom;

    /**
     * The pom file.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.name}-pom.xml")
    protected File targetPom;

    /**
     * The user configuration
     */
    @Parameter
    protected DependencySet dependencies;

    /**
     * The conflict checks configured by the user
     */
    @Parameter
    protected ExternalBomConflictCheckSet checkConflicts;

    /**
     * Used to look up Artifacts in the remote repository.
     */
    @Inject
    protected ArtifactResolver artifactResolver;

    /**
     * Used to look up Artifacts in the remote repository.
     */
    @Inject
    protected RepositorySystem repositorySystem;

    /**
     * List of Remote Repositories used by the resolver
     */
    @Parameter(property = "project.remoteArtifactRepositories", readonly = true, required = true)
    protected List<ArtifactRepository> remoteRepositories;

    /**
     * Location of the local repository.
     */
    @Parameter(property = "localRepository", readonly = true, required = true)
    protected ArtifactRepository localRepository;

    @Parameter(defaultValue = "${basedir}/../../components-starter")
    protected File startersDir;

    /**
     * The user configuration
     */
    @Parameter
    protected boolean failOnError = true;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            DependencyManagement mng = project.getDependencyManagement();

            List<Dependency> filteredDependencies = enhance(mng.getDependencies());

            Set<String> externallyManagedDependencies = getExternallyManagedDependencies();
            checkConflictsWithExternalBoms(filteredDependencies, externallyManagedDependencies);

            Document pom = loadBasePom();

            // transform
            overwriteDependencyManagement(pom, filteredDependencies);

            writePom(pom);

        } catch (MojoFailureException ex) {
            throw ex;
        } catch (MojoExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MojoExecutionException("Cannot generate the output BOM file", ex);
        }
    }

    private List<Dependency> enhance(List<Dependency> dependencyList) throws IOException {
        List<Dependency> outDependencies = new ArrayList<>();

        DependencyMatcher inclusions = new DependencyMatcher(dependencies.getIncludes());
        DependencyMatcher exclusions = new DependencyMatcher(dependencies.getExcludes());

        for (Dependency dep : dependencyList) {
            boolean accept = inclusions.matches(dep) && !exclusions.matches(dep)
                    && !(dep.getGroupId().equals("org.apache.camel.springboot")
                    && dep.getArtifactId().startsWith("camel-") && dep.getArtifactId().endsWith("-starter"));
            getLog().debug(dep + (accept ? " included in the BOM" : " excluded from BOM"));

            if (dep.getArtifactId().startsWith("camel-") && dep.getArtifactId().endsWith("-parent")) {
                dep.setType("pom");
            }

            // skip test-jars
            boolean testJar = dep.getType() != null && dep.getType().equals("test-jar");
            boolean sourcesJar = dep.getClassifier() != null && dep.getClassifier().equals("sources");

            if (accept) {
                if (testJar) {
                    getLog().debug(dep + " test-jar excluded from BOM");
                } else if (sourcesJar) {
                    getLog().debug(dep + " source-jar excluded from BOM");
                } else {
                    outDependencies.add(dep);
                }
            } else {
                // lets log a WARN if some Camel JARs was excluded
                if (dep.getGroupId().startsWith("org.apache.camel")) {
                    getLog().warn(dep + " excluded from BOM");
                }
            }
        }

        Files.list(startersDir.toPath()).filter(Files::isDirectory)
                // must have a pom.xml to be active
                .filter(d -> {
                    File pom = new File(d.toFile(), "pom.xml");
                    return pom.isFile() && pom.exists();
                }).map(dir -> {
                    Dependency dep = new Dependency();
                    dep.setGroupId("org.apache.camel.springboot");
                    dep.setArtifactId(dir.getFileName().toString());
                    dep.setVersion("${project.version}");
                    return dep;
                }).forEach(outDependencies::add);

        // include core starters
        Dependency dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-spring-boot-engine-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-spring-boot-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-spring-boot-xml-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);

        // include dsl starters
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-cli-connector-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-componentdsl-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-dsl-modeline-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-endpointdsl-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-java-joor-dsl-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-xml-io-dsl-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-xml-jaxb-dsl-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);
        dep = new Dependency();
        dep.setGroupId("org.apache.camel.springboot");
        dep.setArtifactId("camel-yaml-dsl-starter");
        dep.setVersion("${project.version}");
        outDependencies.add(dep);

        outDependencies.sort(Comparator.comparing(d -> (d.getGroupId() + ":" + d.getArtifactId())));

        return outDependencies;
    }

    private Document loadBasePom() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document pom = builder.parse(sourcePom);

        XPath xpath = XPathFactory.newInstance().newXPath();

        XPathExpression parentVersion = xpath.compile("/project/parent/version");
        setActualVersion(pom, parentVersion);

        XPathExpression projectVersion = xpath.compile("/project/version");
        setActualVersion(pom, projectVersion);

        return pom;
    }

    private void setActualVersion(Document pom, XPathExpression path) throws XPathExpressionException {
        Node node = (Node) path.evaluate(pom, XPathConstants.NODE);
        if (node != null && node.getTextContent() != null
                && node.getTextContent().trim().equals("${project.version}")) {
            node.setTextContent(project.getVersion());
        }
    }

    private void writePom(Document pom) throws Exception {
        XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("//text()[normalize-space(.) = '']");
        NodeList emptyNodes = (NodeList) xpath.evaluate(pom, XPathConstants.NODESET);

        // Remove empty text nodes
        for (int i = 0; i < emptyNodes.getLength(); i++) {
            Node emptyNode = emptyNodes.item(i);
            emptyNode.getParentNode().removeChild(emptyNode);
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(pom);

        targetPom.getParentFile().mkdirs();

        String content;
        try (StringWriter out = new StringWriter()) {
            StreamResult result = new StreamResult(out);
            transformer.transform(source, result);
            content = out.toString();
        }

        // Fix header formatting problem
        content = content.replaceFirst("-->", "-->\n");
        writeFileIfChanged(content, targetPom);
    }

    private void writeFileIfChanged(String content, File file) throws IOException {
        boolean write = true;

        if (file.exists()) {
            try (FileReader fr = new FileReader(file)) {
                String oldContent = IOUtils.toString(fr);
                if (!content.equals(oldContent)) {
                    getLog().info("File: " + file.getAbsolutePath() + " is updated");
                    fr.close();
                } else {
                    getLog().info("File " + file.getAbsolutePath() + " is not changed");
                    write = false;
                }
            }
        } else {
            File parent = file.getParentFile();
            parent.mkdirs();
        }

        if (write) {
            try (FileWriter fw = new FileWriter(file)) {
                IOUtils.write(content, fw);
            }
        }
    }

    private void overwriteDependencyManagement(Document pom, List<Dependency> dependencies) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        XPathExpression expr = xpath.compile("/project/dependencyManagement/dependencies");

        NodeList nodes = (NodeList) expr.evaluate(pom, XPathConstants.NODESET);
        if (nodes.getLength() == 0) {
            throw new IllegalStateException(
                    "No dependencies found in the dependencyManagement section of the current pom");
        }

        Node dependenciesSection = nodes.item(0);
        // cleanup the dependency management section
        while (dependenciesSection.hasChildNodes()) {
            Node child = dependenciesSection.getFirstChild();
            dependenciesSection.removeChild(child);
        }

        for (Dependency dep : dependencies) {

            if ("target".equals(dep.getArtifactId())) {
                // skip invalid artifact that somehow gets included
                continue;
            }

            Element dependencyEl = pom.createElement("dependency");

            Element groupIdEl = pom.createElement("groupId");
            groupIdEl.setTextContent(dep.getGroupId());
            dependencyEl.appendChild(groupIdEl);

            Element artifactIdEl = pom.createElement("artifactId");
            artifactIdEl.setTextContent(dep.getArtifactId());
            dependencyEl.appendChild(artifactIdEl);

            Element versionEl = pom.createElement("version");
            versionEl.setTextContent(dep.getVersion());
            dependencyEl.appendChild(versionEl);

            if (!"jar".equals(dep.getType())) {
                Element typeEl = pom.createElement("type");
                typeEl.setTextContent(dep.getType());
                dependencyEl.appendChild(typeEl);
            }

            if (dep.getClassifier() != null) {
                Element classifierEl = pom.createElement("classifier");
                classifierEl.setTextContent(dep.getClassifier());
                dependencyEl.appendChild(classifierEl);
            }

            if (dep.getScope() != null && !"compile".equals(dep.getScope())) {
                Element scopeEl = pom.createElement("scope");
                scopeEl.setTextContent(dep.getScope());
                dependencyEl.appendChild(scopeEl);
            }

            if (dep.getExclusions() != null && !dep.getExclusions().isEmpty()) {

                Element exclsEl = pom.createElement("exclusions");

                for (Exclusion e : dep.getExclusions()) {
                    Element exclEl = pom.createElement("exclusion");

                    Element groupIdExEl = pom.createElement("groupId");
                    groupIdExEl.setTextContent(e.getGroupId());
                    exclEl.appendChild(groupIdExEl);

                    Element artifactIdExEl = pom.createElement("artifactId");
                    artifactIdExEl.setTextContent(e.getArtifactId());
                    exclEl.appendChild(artifactIdExEl);

                    exclsEl.appendChild(exclEl);
                }

                dependencyEl.appendChild(exclsEl);
            }

            dependenciesSection.appendChild(dependencyEl);
        }

    }

    private void checkConflictsWithExternalBoms(Collection<Dependency> dependencies, Set<String> external)
            throws MojoFailureException {
        Set<String> errors = new TreeSet<>();
        for (Dependency d : dependencies) {
            String key = comparisonKey(d);
            if (external.contains(key)) {
                errors.add(key);
            }
        }

        if (failOnError && errors.size() > 0) {
            StringBuilder msg = new StringBuilder();
            msg.append("Found ").append(errors.size())
                    .append(" conflicts between the current managed dependencies and the external BOMS:\n");
            for (String error : errors) {
                msg.append(" - ").append(error).append("\n");
            }

            throw new MojoFailureException(msg.toString());
        }
    }

    private Set<String> getExternallyManagedDependencies() throws Exception {
        Set<String> provided = new HashSet<>();
        if (checkConflicts != null && checkConflicts.getBoms() != null) {
            for (ExternalBomConflictCheck check : checkConflicts.getBoms()) {
                Set<String> bomProvided = getProvidedDependencyManagement(check.getGroupId(), check.getArtifactId(),
                        check.getVersion());
                provided.addAll(bomProvided);
            }
        }

        return provided;
    }

    private Set<String> getProvidedDependencyManagement(String groupId, String artifactId, String version)
            throws Exception {
        return getProvidedDependencyManagement(groupId, artifactId, version, new TreeSet<>());
    }

    private Set<String> getProvidedDependencyManagement(String groupId, String artifactId, String version,
                                                        Set<String> gaChecked) throws Exception {
        String ga = groupId + ":" + artifactId;
        gaChecked.add(ga);
        Artifact bom = resolveArtifact(groupId, artifactId, version, "pom");
        MavenProject bomProject = loadExternalProjectPom(bom.getFile());

        Set<String> provided = new HashSet<>();
        if (bomProject.getDependencyManagement() != null
                && bomProject.getDependencyManagement().getDependencies() != null) {
            for (Dependency dep : bomProject.getDependencyManagement().getDependencies()) {
                if ("pom".equals(dep.getType()) && "import".equals(dep.getScope())) {
                    String subGa = dep.getGroupId() + ":" + dep.getArtifactId();
                    if (!gaChecked.contains(subGa)) {
                        Set<String> sub = getProvidedDependencyManagement(dep.getGroupId(), dep.getArtifactId(),
                                resolveVersion(bomProject, dep.getVersion()), gaChecked);
                        provided.addAll(sub);
                    }
                } else {
                    provided.add(comparisonKey(dep));
                }
            }
        }

        return provided;
    }

    private String resolveVersion(MavenProject project, String version) {
        if (version.contains("${")) {
            int start = version.indexOf("${");
            int end = version.indexOf("}");
            if (end > start) {
                String prop = version.substring(start + 2, end);
                String resolved = project.getProperties().getProperty(prop);
                if (resolved != null) {
                    // may contain yet another placeholder
                    if (resolved.contains("${")) {
                        resolved = resolveVersion(project, resolved);
                    }
                    version = version.substring(0, start) + resolved + version.substring(end + 1);
                }
            }
        }
        return version;
    }

    private String comparisonKey(Dependency dependency) {
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
                + (dependency.getType() != null ? dependency.getType() : "jar");
    }

    private Artifact resolveArtifact(String groupId, String artifactId, String version, String type) throws Exception {
        Artifact art = repositorySystem.createArtifact(groupId, artifactId, version, "runtime", type);

        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setRemoteRepositories(remoteRepositories).setLocalRepository(localRepository);

        return artifactResolver.resolveArtifact(buildingRequest, art).getArtifact();
    }

    private MavenProject loadExternalProjectPom(File pomFile) throws Exception {
        try (FileReader reader = new FileReader(pomFile)) {
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            Model model = mavenReader.read(reader);

            MavenProject project = new MavenProject(model);
            project.setFile(pomFile);
            return project;
        }
    }

}
