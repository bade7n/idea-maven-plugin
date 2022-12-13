package com.github.zhve.ideaplugin;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vasiliy Zhukov
 * @since 07/26/2010
 */
public abstract class IdeaPluginMojoBase extends AbstractMojo {
    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private MavenProjectBuilder projectBuilder;

    @Component(role = ArtifactMetadataSource.class, hint = "maven")
    private ArtifactMetadataSource artifactMetadataSource;

    @Parameter(property = "reactorProjects", required = true, readonly = true)
    private List<MavenProject> reactorProjects;

    @Parameter(property = "localRepository", required = true, readonly = true)
    private ArtifactRepository localRepository;

    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> compilePath;

    private ArtifactHolder artifactHolder;
    private VelocityWorker velocityWorker;
    private MavenProject project;

    // Getters

    public List<MavenProject> getReactorProjects() {
        return reactorProjects;
    }

    protected ArtifactHolder getArtifactHolder() {
        return artifactHolder;
    }

    public MavenProject getProject() {
        return project;
    }

    protected VelocityWorker getVelocityWorker() {
        return velocityWorker;
    }

    // AbstractMojo

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        try {
            ArtifactDependencyResolver resolver = new ArtifactDependencyResolver(getLog(), artifactFactory, artifactResolver, localRepository, artifactMetadataSource);
            artifactHolder = new ArtifactHolder(getLog(), resolver, reactorProjects);
            velocityWorker = new VelocityWorker();
            for (MavenProject project : reactorProjects) {
                this.project = project;
                doExecute();
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    protected abstract void doExecute() throws Exception;

    // Velocity Bindings

    public List<Artifact> getDependencies(MavenProject project) {
        return artifactHolder.getDependencies(project);
    }

    public List<Artifact> getAllDependencies() {
        return artifactHolder.getAllDependencies();
    }

    public boolean isReactorArtifact(Artifact artifact) throws MojoExecutionException {
        boolean isReactorArtifact = artifactHolder.isReactorArtifact(artifact);
        try {
            if (!isReactorArtifact && artifact.hasClassifier()) {
                if ("classes".equalsIgnoreCase(artifact.getClassifier())) {
                    isReactorArtifact = artifactHolder.isReactorArtifact(createWarArtifact(artifact));
                } else if ("tests".equalsIgnoreCase(artifact.getClassifier())) {
                    isReactorArtifact = artifactHolder.isReactorArtifact(createTestArtifact(artifact));
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error while generation iml files for " + project.getBasedir() + " artifact: " +  artifact.toString());
        }
        return isReactorArtifact;
    }

    public static DefaultArtifact createWarArtifact(Artifact artifact) {
        VersionRange versionRange = artifact.getVersionRange();
        if(versionRange == null)
            versionRange = VersionRange.createFromVersion(artifact.getVersion());
        else {
            versionRange = versionRange.cloneOf();
        }
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), versionRange.cloneOf(), artifact.getScope(), "war", null, artifact.getArtifactHandler(), artifact.isOptional());
    }

    public static DefaultArtifact createTestArtifact(Artifact artifact) {
        VersionRange versionRange = artifact.getVersionRange();
        if(versionRange == null)
            versionRange = VersionRange.createFromVersion(artifact.getVersion());
        else {
            versionRange = versionRange.cloneOf();
        }
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), versionRange.cloneOf(), artifact.getScope(), "jar", null, new DefaultArtifactHandler(), artifact.isOptional());
    }

    public String getScope(Artifact artifact) {
        if (Artifact.SCOPE_PROVIDED.equalsIgnoreCase(artifact.getScope())) return "PROVIDED";
        if (Artifact.SCOPE_TEST.equalsIgnoreCase(artifact.getScope())) return "TEST";
        if (Artifact.SCOPE_RUNTIME.equalsIgnoreCase(artifact.getScope())) return "RUNTIME";
        return "COMPILE";
    }

    public boolean isWebFriendlyScope(Artifact artifact) {
        return !Artifact.SCOPE_PROVIDED.equalsIgnoreCase(artifact.getScope()) && !Artifact.SCOPE_TEST.equalsIgnoreCase(artifact.getScope());
    }

    public Map<String, String> getVcsMapping() {
        return Collections.emptyMap();
    }

    public String getModuleLibraryJar(Artifact artifact) {
        return localRepository.pathOf(artifact);
    }

    public String getModuleLibraryJavadocs(Artifact artifact) {
        String path = localRepository.pathOf(artifact);
        return path.substring(0, path.length() - 4) + "-javadoc.jar";
    }

    public String getModuleLibrarySources(Artifact artifact) {
        String path = localRepository.pathOf(artifact);
        return path.substring(0, path.length() - 4) + "-sources.jar";
    }

    public String getLocalRepositoryBasePath() {
        return localRepository.getBasedir();
    }

    public String getReactorArtifactJarName(Artifact artifact) {
        return artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar";
    }

    public List<String> getReactorPaths() {
        List<String> list = new ArrayList<String>();
        list.add(new File(project.getFile().getParentFile(), project.getArtifactId() + ".iml").getAbsolutePath());
        for (Object collectedProject : project.getCollectedProjects()) {
            MavenProject reactorProject = (MavenProject) collectedProject;
            list.add(new File(reactorProject.getFile().getParentFile(), reactorProject.getArtifactId() + ".iml").getAbsolutePath());
        }
        return list;
    }
}
