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

import org.gradle.api.Project
import org.gradle.api.file.FileTree

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DSL declaration of Gradle
 */
public class RclJavaPluginExtension {

    private final Logger logger = LoggerFactory.getLogger(RclJavaPluginExtension);

    // Constants
    private final static String DEPENDENCIES_SEPARATOR = ":"
    private final static String BINARY_FOLDER = "lib"
    private final static String BINARY_EXT = "*.so"
    private final static String PROPERTY_ANDROID = "android"
    private final static String PROPERTY_AMENT   = "ament"

    public final static String PROPERTY_AMENT_DEPENDENCIES = PROPERTY_AMENT + ".dependencies"
    public final static String PROPERTY_AMENT_BUILDSPACE   = PROPERTY_AMENT + ".build_space"
    public final static String PROPERTY_AMENT_INSTALLSPACE = PROPERTY_AMENT + ".install_space"

    public final static String PROPERTY_AMENT_ANDROIDABI   = PROPERTY_AMENT + ".android_abi"
    public final static String PROPERTY_AMENT_ANDROIDNDK   = PROPERTY_AMENT + ".android_ndk"
    public final static String PROPERTY_AMENT_ANDROIDSTL   = PROPERTY_AMENT + ".android_stl"

    // Internal var
    protected Project project

    // Properties
    public String dependencies = null
    public String buildSpace   = null
    public String installSpace = null

    public String androidAbi = null
    public String androidNdk = null
    public String androidStl = null

    boolean generateEclipse = false

    /** Constructor */
    public RclJavaPluginExtension() {
        logger.info("Initialze Extention class.")
        this.project = null;
    }

    /**
     * Load Extention from Project.
     *
     * @param project from load
     * @param extension
     *
     * @return Extention Instance.
     */
    public void loadExtension(Project project) {
        this.project = project

        if (Strings.isNullOrEmpty(this.buildSpace)
                && this.project.hasProperty(PROPERTY_AMENT_BUILDSPACE)
                && !this.project.getProperties().get(PROPERTY_AMENT_BUILDSPACE).endsWith(this.project.name)) {
            return
        }

        // Load all properties.
        this.buildSpace   = this.loadProperty(PROPERTY_AMENT_BUILDSPACE)
        this.dependencies = this.loadProperty(PROPERTY_AMENT_DEPENDENCIES)
        this.installSpace = this.loadProperty(PROPERTY_AMENT_INSTALLSPACE)

        // Android case.
        if(project.hasProperty(PROPERTY_ANDROID)) {
            this.androidAbi = this.loadProperty(PROPERTY_AMENT_ANDROIDABI)
            this.androidNdk = this.loadProperty(PROPERTY_AMENT_ANDROIDNDK)
            this.androidStl = this.loadProperty(PROPERTY_AMENT_ANDROIDSTL)
        }
    }

    public boolean isConfigure() {
        return this.dependencies != null &&
               this.buildSpace   != null &&
               this.installSpace != null
    }

    /**
     *
     * @return
     */
    public FileTree[] getNativeDependencies() {
        def result = []

        if (this.dependencies != null) {
            this.dependencies.split(DEPENDENCIES_SEPARATOR).each {
                def libFolder = [new File(it).parentFile.parentFile, BINARY_FOLDER].join(File.separator)
                def nativeLibs = this.project.fileTree(dir: libFolder, include: BINARY_EXT)
                result.add(nativeLibs)
            }
        }

        result.each({
            logger.info(it)
        })

        return result;
    }

    /**
     * Load Extention Property from Project Property.
     *
     * @param project where load the property.
     * @param defaultValue default value if not define in project.
     * @param propertyName Property name.
     *
     * @return value for Extention.
     */
    private String loadProperty(String propertyName) {
        String result = null

        if (Strings.isNullOrEmpty(propertyName)) {
            throw new NullPointerException("Property name is required !");
        }

        if (this.project != null) {
            if (this.project.hasProperty(propertyName)) {
                result = this.project.getProperties().get(propertyName)
            } else {
//                throw new MissingPropertyException("Missing Ament Gradle property: ${propertyName}")
            }
        }

        return result;
    }
}
