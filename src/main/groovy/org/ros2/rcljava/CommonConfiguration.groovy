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

import org.gradle.api.JavaVersion
import org.gradle.api.Project

/**
 * Configures a Java ros2 project.
 *
 * - apply java plugin to project
 * - move jar to ament install space
 * - regen eclipse project if needed
 *
 * @author Erwan Le Huitouze <erwan.lehuitouze@gmail.com>
 */
public abstract class CommonConfiguration {

    protected RclJavaPluginExtension extension

    /** True if ament as trigger the build, else is gradle. */
    protected boolean isAmentBuild

    void configure(final Project project) {
        this.loadDependenciesFromCache(project)

        if (this.isAmentBuild && project.ament.buildSpace != null) {
            project.buildDir = new File(project.ament.buildSpace, 'gradle');
        }

        this.updateDependenciesCache(project)
    }

    void afterEvaluate(final Project project, RclJavaPluginExtension extension) {
        this.extension = extension

        //Set java properties
        this.updateJavaProperties(project)
    }

    /**
     * Update java compule properties.
     * Update source and target compatibilty.
     **/
    protected void updateJavaProperties(Project project) {
        if (project.sourceCompatibility < JavaVersion.VERSION_1_6) {
            project.sourceCompatibility = JavaVersion.VERSION_1_6
        }

        if (project.targetCompatibility < JavaVersion.VERSION_1_6) {
            project.targetCompatibility = JavaVersion.VERSION_1_6
        }
    }

    void updateDependenciesCache(Project project) {
        if (project.ament.dependencies != null
                && project.ament.buildSpace != null
                && project.ament.installSpace != null) {

            def separator = System.getProperty("line.separator")
            def properties = new File(project.projectDir, ".ament_dependencies.properties")

            properties.write('ament.build_space=')
            properties.append(project.ament.buildSpace)
            properties.append(separator)
            properties.append('ament.install_space=')
            properties.append(project.ament.installSpace)
            properties.append(separator)
            properties.append('ament.dependencies=')
            properties.append(project.ament.dependencies)

            if (project.ament.androidStl != null) {
                properties.append(separator)
                properties.append('ament.android_stl=')
                properties.append(project.ament.androidStl)
            }

            if (project.ament.androidAbi != null) {
                properties.append(separator)
                properties.append('ament.android_abi=')
                properties.append(project.ament.androidAbi)
            }

            if (project.ament.androidNdk != null) {
                properties.append(separator)
                properties.append('ament.android_ndk=')
                properties.append(project.ament.androidNdk)
                properties.append(separator)
            }
        }
    }

    void loadDependenciesFromCache(Project project) {
        if (project.ament.dependencies == null
                || project.ament.buildSpace == null
                || project.ament.installSpace == null
                || project.ament.androidStl == null
                || project.ament.androidAbi == null
                || project.ament.androidNdk == null) {

            def propertiesFile = new File(project.projectDir, ".ament_dependencies.properties")

            if (propertiesFile.exists()) {
                Properties props = new Properties()
                props.load(new FileInputStream(propertiesFile))
                props.each { prop ->
                    if (prop.key == 'ament.build_space') {
                        project.ament.buildSpace = prop.value
                    } else if (prop.key == 'ament.dependencies') {
                        project.ament.dependencies = prop.value
                    } else if (prop.key == 'ament.install_space') {
                        project.ament.installSpace = prop.value
                    } else if (prop.key == 'ament.android_stl') {
                        project.ament.androidStl = prop.value
                    } else if (prop.key == 'ament.android_abi') {
                        project.ament.androidAbi = prop.value
                    } else if (prop.key == 'ament.android_ndk') {
                        project.ament.androidNdk = prop.value
                    }
                }
            }
        } else {
            this.isAmentBuild = true
        }
    }
}
