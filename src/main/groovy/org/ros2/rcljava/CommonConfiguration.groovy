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

import org.gradle.api.JavaVersion
import org.gradle.api.Project

import org.ros2.rcljava.RclJavaPluginExtension as Extension

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

    private static final String FILE_DEPENDANCY = ".ament_dependencies.properties"

    protected RclJavaPluginExtension extension

    /** True if ament as trigger the build, else is gradle. */
    protected boolean isAmentBuild = false

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
        updateJavaProperties(project)
    }

    /**
     * Update java compule properties.
     * Update source and target compatibilty.
     **/
    protected static void updateJavaProperties(Project project) {
        if (project.sourceCompatibility < JavaVersion.VERSION_1_6) {
            project.sourceCompatibility = JavaVersion.VERSION_1_6
        }

        if (project.targetCompatibility < JavaVersion.VERSION_1_6) {
            project.targetCompatibility = JavaVersion.VERSION_1_6
        }
    }

    static String EOL = System.getProperty("line.separator")

    private static void appendProperty(StringBuilder builder, String extension, String value) {
        if (!Strings.isNullOrEmpty(extension) && !Strings.isNullOrEmpty(value)) {
            builder.append(extension)
            builder.append('=')
            builder.append(value)
            builder.append(EOL)
        }
    }

    /**
     *
     * @param project
     */
    void updateDependenciesCache(Project project) {
        RclJavaPluginExtension extension = project.ament

        if (extension.isConfigure()) {
            StringBuilder builder = new StringBuilder()
            appendProperty(builder, Extension.PROPERTY_AMENT_BUILDSPACE,   extension.buildSpace)
            appendProperty(builder, Extension.PROPERTY_AMENT_INSTALLSPACE, extension.installSpace)
            appendProperty(builder, Extension.PROPERTY_AMENT_DEPENDENCIES, extension.dependencies)
            appendProperty(builder, Extension.PROPERTY_AMENT_ANDROIDSTL,   extension.androidStl)
            appendProperty(builder, Extension.PROPERTY_AMENT_ANDROIDABI,   extension.androidAbi)
            appendProperty(builder, Extension.PROPERTY_AMENT_ANDROIDNDK,   extension.androidNdk)

            File propertiesFile = new File(project.projectDir, FILE_DEPENDANCY)
            propertiesFile.write(builder.toString())
        }
    }

    /**
     *
     * @param project
     */
    void loadDependenciesFromCache(Project project) {
        if (project.ament.dependencies == null
                || project.ament.buildSpace == null
                || project.ament.installSpace == null
                || project.ament.androidStl == null
                || project.ament.androidAbi == null
                || project.ament.androidNdk == null) {

            File propertiesFile = new File(project.projectDir, FILE_DEPENDANCY)
            if (propertiesFile.exists()) {
                Properties props = new Properties()
                props.load(new FileInputStream(propertiesFile))
                props.each { prop ->
                    if (prop.key == Extension.PROPERTY_AMENT_BUILDSPACE) {
                        project.ament.buildSpace = prop.value
                    } else
                    if (prop.key == Extension.PROPERTY_AMENT_DEPENDENCIES) {
                        project.ament.dependencies = prop.value
                    } else
                    if (prop.key == Extension.PROPERTY_AMENT_INSTALLSPACE) {
                        project.ament.installSpace = prop.value
                    } else
                    if (prop.key == Extension.PROPERTY_AMENT_ANDROIDSTL) {
                        project.ament.androidStl = prop.value
                    } else
                    if (prop.key == Extension.PROPERTY_AMENT_ANDROIDABI) {
                        project.ament.androidAbi = prop.value
                    } else
                    if (prop.key == Extension.PROPERTY_AMENT_ANDROIDNDK) {
                        project.ament.androidNdk = prop.value
                    }
                }
            }
        } else {
            this.isAmentBuild = true
        }
    }
}
