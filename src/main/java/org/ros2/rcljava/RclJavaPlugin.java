/* Copyright 2016 Open Source Robotics Foundation, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ros2.rcljava;

import java.io.File;
import java.util.Date;
import java.util.jar.Attributes;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;

/**
 * Configures a Java ros2 project.
 *
 * - apply java plugin to project
 * - move jar to ament install space
 * - regen eclipse project if needed
 *
 * @author Erwan Le Huitouze <erwan.lehuitouze@gmail.com>
 */
public class RclJavaPlugin implements Plugin<Project> {

    private static final String PROPERTY_SOURCE_COMPATIBILITY = "sourceCompatibility";
    private static final String PROPERTY_TARGET_COMPATIBILITY = "targetCompatibility";

    private static final String PROPERTY_SOURCE_SETS = "sourceSets";
    private static final String PROPERTY_ARCHIVES_BASENAME = "archivesBaseName";

    private static final String PROPERTY_AMENT_DEPENDENCIES = "ament.dependencies";
    private static final String PROPERTY_AMENT_BUILDSPACE = "ament.build_space";
    private static final String PROPERTY_AMENT_INSTALLSPACE = "ament.install_space";

    private RclJavaPluginExtension extension;

    @Override
    public void apply(final Project project) {
        project.getExtensions().create("ament", RclJavaPluginExtension.class);

        //Extend java-plugin
        project.getPluginManager().apply(JavaPlugin.class);

        project.getPluginManager().apply(EclipsePlugin.class);

        //Set java properties
        this.updateJavaProperties(project);

        // after eval
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                // dont continue if its already failed!
                if (project.getState().getFailure() != null)
                    return;

                afterEvaluate(project);
            }
        });
    }

    protected void afterEvaluate(final Project project) {
        this.extension = this.loadExtension(project);

        //Update jar
        this.updateJar(project);

        //Update dependencies
        this.updateDependencies(project, JavaPlugin.COMPILE_CONFIGURATION_NAME, "java");
        this.updateDependencies(project, JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME, "lib");

        //Update sourceSetOutput
        this.updateSourceSetOutput(project);

        //Update jar manifest
        this.updateManifest(project);

        //Update test task
        this.updateTestTask(project);

        if (this.extension.isGenerateEclipse()) {
            //Extend java-plugin
            Task task = project.getTasks().getByName(EclipsePlugin.getECLIPSE_TASK_NAME());
            Task taskCompile = project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME);
            taskCompile.dependsOn(task);
        }
    }

    /**
     * Update java compule properties.
     * Update source and target compatibilty.
     **/
    private void updateJavaProperties(final Project project) {
        project.setProperty(PROPERTY_SOURCE_COMPATIBILITY, JavaVersion.VERSION_1_6);
        project.setProperty(PROPERTY_TARGET_COMPATIBILITY, JavaVersion.VERSION_1_6);
    }

    /**
     * Update project dependencies.
     * Add jar dependencies from ament configuration.
     **/
    private void updateDependencies(final Project project, final String configuration, final String folder) {
        final DependencySet compileDeps = project.getConfigurations().getByName(configuration).getDependencies();

        project.getGradle().addListener(new DependencyResolutionListener() {

            @Override
            public void beforeResolve(ResolvableDependencies paramResolvableDependencies) {
                if (extension.getDependencies() != null) {
                    for (String dependency : extension.getDependencies().split(":")) {
                        ConfigurableFileTree tree = project.fileTree(new File(dependency, folder));
                        tree.include("*.jar");
                        compileDeps.add(project.getDependencies().create(tree));
                    }

                    project.getGradle().removeListener(this);
                }
            }

            @Override
            public void afterResolve(ResolvableDependencies paramResolvableDependencies) {

            }
        });
    }

    /**
     * Change compiled sources output.
     **/
    private void updateSourceSetOutput(final Project project) {
        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(final JavaPlugin plugin) {
                if (extension.getBuildSpace() != null) {
                    String sourceDir = extension.getBuildSpace();
                    SourceSet mainSourceSet = ((SourceSetContainer) project.getProperties()
                            .get(PROPERTY_SOURCE_SETS)).getByName(SourceSet.MAIN_SOURCE_SET_NAME);

                    mainSourceSet.getOutput().setClassesDir(
                            new File(sourceDir, "classes" + File.separator + SourceSet.MAIN_SOURCE_SET_NAME));
                    mainSourceSet.getOutput().setResourcesDir(
                            new File(sourceDir, "resources" + File.separator + SourceSet.MAIN_SOURCE_SET_NAME));
                }
            }
        });
    }

    /**
     * Change jar output.
     **/
    private void updateJar(final Project project) {
        if (extension.getInstallSpace() != null) {

            Jar jarTask = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);

            jarTask.setDestinationDir(new File(
                    extension.getInstallSpace(),
                    "share" + File.separator + project.getName() + File.separator + "java"));
        }
    }

    private void updateManifest(final Project project) {
        Jar jarTask = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);

        org.gradle.api.java.archives.Attributes attributes = jarTask.getManifest().getAttributes();

        if (!attributes.containsKey(Attributes.Name.IMPLEMENTATION_TITLE.toString())) {
            attributes.put(Attributes.Name.IMPLEMENTATION_TITLE.toString(),
                    project.getProperties().get(PROPERTY_ARCHIVES_BASENAME));
        }

        if (!attributes.containsKey(Attributes.Name.IMPLEMENTATION_VERSION.toString())) {
            attributes.put(Attributes.Name.IMPLEMENTATION_VERSION.toString(), project.getVersion());
        }

        if (!attributes.containsKey("Built-By")) {
            attributes.put("Built-By", System.getProperty("user.name"));
        }

        if (!attributes.containsKey("Built-Date")) {
            attributes.put("Built-Date", new Date());
        }

        if (!attributes.containsKey("Built-JDK")) {
            attributes.put("Built-JDK", System.getProperty("java.version"));
        }
    }

    private void updateTestTask(final Project project) {
        if (extension.getInstallSpace() != null) {
            Test testTask = (Test) project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);

            ConfigurableFileTree tree = project.fileTree(new File(extension.getInstallSpace()).getParentFile());
            tree.include("**/lib/*.so");

            final StringBuilder paths = new StringBuilder();

            tree.visit(new FileVisitor() {

                @Override
                public void visitFile(FileVisitDetails paramFileVisitDetails) {

                }

                @Override
                public void visitDir(FileVisitDetails paramFileVisitDetails) {
                    if (paramFileVisitDetails.getName().endsWith("lib")) {
                        paths.append(paramFileVisitDetails.getFile().getAbsolutePath());
                        paths.append(":");
                    }
                }
            });

            testTask.getSystemProperties().put(
                    "java.library.path",
                    paths.toString() + System.getProperty("java.library.path"));
        }
    }

    private RclJavaPluginExtension loadExtension(final Project project) {
        RclJavaPluginExtension extension = project.getExtensions().findByType(RclJavaPluginExtension.class);

        if (extension == null) {
            extension = new RclJavaPluginExtension();
        }

        if ((extension.getBuildSpace() == null
                || extension.getBuildSpace().length() == 0)
                && project.hasProperty(PROPERTY_AMENT_BUILDSPACE)) {
            extension.setBuildSpace((String) project.getProperties().get(PROPERTY_AMENT_BUILDSPACE));
        }

        if ((extension.getDependencies() == null
                || extension.getDependencies().length() == 0)
                && project.hasProperty(PROPERTY_AMENT_DEPENDENCIES)) {
            extension.setDependencies((String) project.getProperties().get(PROPERTY_AMENT_DEPENDENCIES));
        }

        if ((extension.getInstallSpace() == null
                || extension.getInstallSpace().length() == 0)
                && project.hasProperty(PROPERTY_AMENT_INSTALLSPACE)) {
            extension.setInstallSpace((String) project.getProperties().get(PROPERTY_AMENT_INSTALLSPACE));
        }

        return extension;
    }
}
