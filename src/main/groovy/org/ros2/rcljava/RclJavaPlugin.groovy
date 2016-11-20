/* Copyright 2016 Open Source Robotics Foundation, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package org.ros2.rcljava

import java.io.File
import java.util.ArrayList
import java.util.Date
import java.util.Map.Entry
import java.util.List
import java.util.jar.Attributes

import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.plugins.ide.eclipse.EclipsePlugin

import com.google.common.io.Files

/**
 * Configures a Java ros2 project.
 *
 * - apply java plugin to project
 * - move jar to ament install space
 * - regen eclipse project if needed
 *
 * @author Erwan Le Huitouze <erwan.lehuitouze@gmail.com>
 */
class RclJavaPlugin implements Plugin<Project> {

    private String PROPERTY_AMENT_DEPENDENCIES = "ament.dependencies"
    private String PROPERTY_AMENT_BUILDSPACE = "ament.build_space"
    private String PROPERTY_AMENT_INSTALLSPACE = "ament.install_space"

    private RclJavaPluginExtension extension

    void apply(Project project) {
        project.extensions.create("ament", RclJavaPluginExtension)
        project.ament.extensions.scripts = project.container(NodeScript)

        if (!this.isAndroidProject(project)) {
            //Extend java-plugin
            project.getPluginManager().apply(JavaPlugin.class)
            project.getPluginManager().apply(EclipsePlugin.class)
        }

        CommonConfiguration configuration

        if (!this.isAndroidProject(project)) {
            configuration = new JavaConfiguration()
        } else {
            configuration = new AndroidConfiguration()
        }

        configuration.configure(project)

        project.afterEvaluate {
            this.afterEvaluate(project)
            configuration.afterEvaluate(project, this.extension)
        }
    }

    private void afterEvaluate(final Project project) {
        this.extension = this.loadExtension(project)

        if (this.extension.isGenerateEclipse()) {
            //Extend java-plugin
            Task task = project.getTasks().getByName(EclipsePlugin.getECLIPSE_TASK_NAME())
            Task taskCompile = project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
            taskCompile.dependsOn(task)
        }
    }

    private boolean isAndroidProject(Project project) {
        return project.plugins.hasPlugin("com.android.application")
    }

    private RclJavaPluginExtension loadExtension(Project project) {
        RclJavaPluginExtension extension = project.ament

        if (extension == null) {
            extension = new RclJavaPluginExtension()
        }

        if ((extension.buildSpace == null || extension.buildSpace.length() == 0)
                && project.hasProperty(PROPERTY_AMENT_BUILDSPACE)) {
            extension.buildSpace = project.getProperties().get(PROPERTY_AMENT_BUILDSPACE)
        }

        if ((extension.dependencies == null || extension.dependencies.length() == 0)
                && project.hasProperty(PROPERTY_AMENT_DEPENDENCIES)) {
            extension.dependencies = project.getProperties().get(PROPERTY_AMENT_DEPENDENCIES)
        }

        if ((extension.installSpace == null || extension.installSpace.length() == 0)
                && project.hasProperty(PROPERTY_AMENT_INSTALLSPACE)) {
            extension.installSpace = project.getProperties().get(PROPERTY_AMENT_INSTALLSPACE)
        }

        return extension
    }
}
