/* Copyright 2016-2017 Mickael Gaillard <mick.gaillard@gmail.com>
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
package org.ros2.rcljava

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test

import org.gradle.plugins.ide.eclipse.EclipsePlugin

import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoReport

import org.kt3k.gradle.plugin.CoverallsPlugin

/**
 * Configures a Java ros2 project.
 *
 * - apply java plugin to project
 * - move jar to ament install space
 * - regen eclipse project if needed
 *
 * @author Erwan Le Huitouze <erwan.lehuitouze@gmail.com>
 * @author Mickael Gaillard <mick.gaillard@gmail.com>
 */
class RclJavaGradlePlugin implements Plugin<Project> {

    private RclJavaPluginExtension extension

    /**
    * Add ament extention to project.
    * @param project
    */
    private void initalizeExtension(final Project project) {
        this.extension = project.extensions.create(RclJavaPluginExtension.PROPERTY_AMENT, RclJavaPluginExtension)
        project.ament.extensions.scripts = project.container(NodeScript) //TODO() : Refactor => Add Runtime config for futur node.
        this.extension.loadExtension(project)
    }

    /**
     * Load Plugins dependancies.
     * @param project
     */
    private void loadPlugins(final Project project) {
        if (hasReport(project)) { // If report enable.
            project.getPluginManager().apply(JacocoPlugin.class)
            project.getPluginManager().apply(CoverallsPlugin.class)
        }

        if (!isAndroidProject(project)) { // If not a Android project.
            project.getPluginManager().apply(JavaPlugin.class)

            if (this.extension.isGenerateEclipse()) {
                project.getPluginManager().apply(EclipsePlugin.class)
            }
        }
    }

    /**
     * Configure Report task.
     * @param project
     */
    private void configureReportTask(final Project project) {
        if (hasReport(project)) {
            project.tasks.withType(JacocoReport) {
                reports {
                    xml.enabled = true // coveralls plugin depends on xml format report
                    html.enabled = true
                }
                finalizedBy 'coveralls'
            }

            project.tasks.withType(Test) {
                finalizedBy 'jacocoTestReport'
    //          dependsOn 'cleanTest'
            }
        }
    }

    @Override
    public void apply(Project project) {
        this.initalizeExtension(project)
        this.loadPlugins(project)

        // Load Configuration.
        CommonConfiguration configuration = null
        if (!isAndroidProject(project)) {
            configuration = new JavaConfiguration()
        } else {
            configuration = new AndroidConfiguration()
        }

        // Override AfterEvaluate event.
        project.afterEvaluate {
            this.afterEvaluate(project)
            configuration.afterEvaluate(project, this.extension)
        }

        this.configureReportTask(project)

        configuration.configure(project)
    }

    private void afterEvaluate(final Project project) {
        if (this.extension == null) {
            logger.quiet('Reload Extention...')
            this.initalizeExtension(project)
        }

        if (this.extension.isGenerateEclipse()) {
            //Extend java-plugin
            Task task = project.getTasks().getByName(EclipsePlugin.getECLIPSE_TASK_NAME())
            Task taskCompile = project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
            taskCompile.dependsOn(task)
        }
    }

    private static boolean isAndroidProject(Project project) {
        boolean result = project.plugins.hasPlugin("com.android.application") || project.plugins.hasPlugin("com.android.library")
        return result
    }

    private static boolean hasReport(Project project) {
        return true; //TODO(mick) : check from property
    }

}
