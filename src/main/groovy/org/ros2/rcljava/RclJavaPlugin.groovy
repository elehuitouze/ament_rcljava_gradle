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

import com.google.common.base.Strings
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
class RclJavaPlugin implements Plugin<Project> {

    private static String PROPERTY_AMENT = "ament"
    private static String PROPERTY_AMENT_DEPENDENCIES = PROPERTY_AMENT + ".dependencies"
    private static String PROPERTY_AMENT_BUILDSPACE   = PROPERTY_AMENT + ".build_space"
    private static String PROPERTY_AMENT_INSTALLSPACE = PROPERTY_AMENT + ".install_space"

    private static String PROPERTY_AMENT_ANDROIDABI   = PROPERTY_AMENT + ".android_abi"
    private static String PROPERTY_AMENT_ANDROIDNDK   = PROPERTY_AMENT + ".android_ndk"
    private static String PROPERTY_AMENT_ANDROIDSTL   = PROPERTY_AMENT + ".android_stl"

    private RclJavaPluginExtension extension

    void apply(Project project) {
        project.extensions.create(PROPERTY_AMENT, RclJavaPluginExtension)
        project.ament.extensions.scripts = project.container(NodeScript)
        project.ament.project = project

        project.getPluginManager().apply(JacocoPlugin.class)
        project.getPluginManager().apply(CoverallsPlugin.class)

        if (!isAndroidProject(project)) {
            //Extend java-plugin
            project.getPluginManager().apply(JavaPlugin.class)
            project.getPluginManager().apply(EclipsePlugin.class)
        }

        loadExtension(project, project.ament)

        CommonConfiguration configuration

        if (!isAndroidProject(project)) {
            configuration = new JavaConfiguration()
        } else {
            configuration = new AndroidConfiguration()
        }        

        project.afterEvaluate {
            this.afterEvaluate(project)
            configuration.afterEvaluate(project, this.extension)
        }

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

        configuration.configure(project)
    }

    private void afterEvaluate(final Project project) {
        this.extension = loadExtension(project)

        if (this.extension.isGenerateEclipse()) {
            //Extend java-plugin
            Task task = project.getTasks().getByName(EclipsePlugin.getECLIPSE_TASK_NAME())
            Task taskCompile = project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
            taskCompile.dependsOn(task)
        }
    }

    private static boolean isAndroidProject(Project project) {
        return project.plugins.hasPlugin("com.android.application") || project.plugins.hasPlugin("com.android.library")
    }

    static RclJavaPluginExtension loadExtension(Project project) {
        RclJavaPluginExtension extension = project.ament

        return loadExtension(project, extension)
    }

    static RclJavaPluginExtension loadExtension(Project project, RclJavaPluginExtension extension) {
        if (extension == null) {
            extension = new RclJavaPluginExtension()
        }

        if (Strings.isNullOrEmpty(extension.buildSpace)
                && project.hasProperty(PROPERTY_AMENT_BUILDSPACE)
                && !project.getProperties().get(PROPERTY_AMENT_BUILDSPACE).endsWith(project.name)) {
            return extension;
        }

        if (Strings.isNullOrEmpty(extension.buildSpace)
                && project.hasProperty(PROPERTY_AMENT_BUILDSPACE)) {
            extension.buildSpace = project.getProperties().get(PROPERTY_AMENT_BUILDSPACE)
        }

        if (Strings.isNullOrEmpty(extension.dependencies)
                && project.hasProperty(PROPERTY_AMENT_DEPENDENCIES)) {
            extension.dependencies = project.getProperties().get(PROPERTY_AMENT_DEPENDENCIES)
        }

        if (Strings.isNullOrEmpty(extension.installSpace)
                && project.hasProperty(PROPERTY_AMENT_INSTALLSPACE)) {
            extension.installSpace = project.getProperties().get(PROPERTY_AMENT_INSTALLSPACE)
        }
        
        if ((extension.androidAbi == null || extension.androidAbi.length() == 0)
                && project.hasProperty(PROPERTY_AMENT_ANDROIDABI)) {
            extension.androidAbi = project.getProperties().get(PROPERTY_AMENT_ANDROIDABI)
        }
        
        if ((extension.androidNdk == null || extension.androidNdk.length() == 0)
                && project.hasProperty(PROPERTY_AMENT_ANDROIDNDK)) {
            extension.androidNdk = project.getProperties().get(PROPERTY_AMENT_ANDROIDNDK)
        }
        
        if ((extension.androidStl == null || extension.androidStl.length() == 0)
                && project.hasProperty(PROPERTY_AMENT_ANDROIDSTL)) {
            extension.androidStl = project.getProperties().get(PROPERTY_AMENT_ANDROIDSTL)
        }

        return extension
    }
}
