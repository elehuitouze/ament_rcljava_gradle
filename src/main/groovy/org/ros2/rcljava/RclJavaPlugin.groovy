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

    def String PROPERTY_SOURCE_COMPATIBILITY = "sourceCompatibility"
    def String PROPERTY_TARGET_COMPATIBILITY = "targetCompatibility"

    def String PROPERTY_SOURCE_SETS = "sourceSets"
    def String PROPERTY_ARCHIVES_BASENAME = "archivesBaseName"

    def String PROPERTY_AMENT_DEPENDENCIES = "ament.dependencies"
    def String PROPERTY_AMENT_BUILDSPACE = "ament.build_space"
    def String PROPERTY_AMENT_INSTALLSPACE = "ament.install_space"

    private RclJavaPluginExtension extension

    void apply(Project project) {
        project.extensions.create("ament", RclJavaPluginExtension)
        project.ament.extensions.scripts = project.container(NodeScript)

        //Extend java-plugin
        project.getPluginManager().apply(JavaPlugin.class)
        project.getPluginManager().apply(EclipsePlugin.class)

        project.afterEvaluate {
            this.afterEvaluate(project)
        }
    }

    void afterEvaluate(final Project project) {
        this.extension = this.loadExtension(project)

        //Set java properties
        this.updateJavaProperties(project)

        this.configurePrepareAssemble(project)

        //Install files
        this.configureInstallFiles(project)

        //Update dependencies
        this.updateDependencies(project, JavaPlugin.COMPILE_CONFIGURATION_NAME, "java")
        this.updateDependencies(project, JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME, "lib")

        //Update sourceSetOutput
        this.updateSourceSetOutput(project)

        //Update jar manifest
        this.updateManifest(project)

        //Update test task
        this.updateTestTask(project)

        this.configureCreateScript(project)

        if (this.extension.isGenerateEclipse()) {
            //Extend java-plugin
            Task task = project.getTasks().getByName(EclipsePlugin.getECLIPSE_TASK_NAME())
            Task taskCompile = project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
            taskCompile.dependsOn(task)
        }
    }

    /**
     * Update java compule properties.
     * Update source and target compatibilty.
     **/
    void updateJavaProperties(Project project) {
        if (project.sourceCompatibility < JavaVersion.VERSION_1_6) {
            project.sourceCompatibility = JavaVersion.VERSION_1_6
        }

        if (project.targetCompatibility < JavaVersion.VERSION_1_6) {
            project.targetCompatibility = JavaVersion.VERSION_1_6
        }
    }

    /**
     * Update project dependencies.
     * Add jar dependencies from ament configuration.
     **/
    void updateDependencies(Project project, String configuration, String folder) {
        if (project.ament.dependencies != null) {
            project.dependencies {
                project.ament.dependencies.split(':').each {
                    compile project.fileTree(dir: new File(it, folder), include: '*.jar')
                }
            }
        }
    }

    /**
     * Change compiled sources output.
     **/
    void updateSourceSetOutput(Project project) {
        if (project.ament.buildSpace != null) {
            project.plugins.withType(JavaPlugin) {
                project.sourceSets {
                    main {
                        output.classesDir = new File(
                            project.ament.buildSpace,
                            "classes" + File.separator + SourceSet.MAIN_SOURCE_SET_NAME)

                        output.resourcesDir = new File(
                            project.ament.buildSpace,
                            "resources" + File.separator + SourceSet.MAIN_SOURCE_SET_NAME)
                    }
                }
            }
        }
    }

    /**
     * Pre-assemble files.
     **/
    void configurePrepareAssemble(Project project) {
        if (project.ament.installSpace != null) {
            def copy = project.tasks.create('prepareAssemble', Copy) {
                from project.configurations.runtime
                from project.configurations.testRuntime
                from 'lib'

                into new File(project.ament.installSpace, "lib" + File.separator + "java")
            }

            copy.group = 'build'
            copy.description = 'Copy libs used by ament in install space'
            project.tasks.getByPath('assemble').dependsOn copy
        }
    }

    /**
     * Install files.
     **/
    void configureInstallFiles(Project project) {
        if (project.ament.installSpace != null) {
            def sync = project.tasks.create('amentInstall', Copy) {
                destinationDir new File(project.ament.installSpace)

                into("share" + File.separator + project.name + File.separator + "java") {
                    from project.file('build/libs/')
                }

                into("bin") {
                    from project.file('build/scripts/')
                }
            }

            sync.group = 'ament'
            sync.description = 'Copy files to ament install folder'
            sync.dependsOn 'jar'
            project.tasks.getByPath('install').dependsOn sync
        }
    }

    void updateManifest(Project project) {
        project.tasks.withType(Jar) {
            manifest.attributes(
                (java.util.jar.Attributes.Name.IMPLEMENTATION_TITLE.toString()) : project.archivesBaseName,
                (java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION.toString()): project.version,
                'Built-By': System.getProperty("user.name"),
                'Built-Date': new Date(),
                'Built-JDK': System.getProperty("java.version")
            )
        }
    }

    void updateTestTask(Project project) {
        if (project.ament.installSpace != null) {
            project.tasks.withType(Test) {
                def path = ""

                FileTree tree = project.fileTree(new File(project.ament.installSpace))
                tree.include "**/lib/*.so"

                tree.visit {element ->
                    if (element.name.endsWith("lib")) {
                        path += element.file.absolutePath + File.pathSeparator
                    }
                }

                systemProperties["java.library.path"] = System.getProperty("java.library.path") + File.pathSeparator + path
            }
        }
    }

    void configureCreateScript(final Project project) {
        if (this.extension.mainClasses != null) {
            for (NodeScript nodescript : this.extension.scripts) {
                def task = project.tasks.create('createScript', CreateStartScripts) {
                    if (nodescript.applicationName != null && nodescript.applicationName.length() > 0) {
                        applicationName nodescript.applicationName
                    } else {
                        applicationName nodescript.name
                    }

                    mainClassName nodescript.mainClassName
                    outputDir new File(project.buildDir, "scripts")

                    if (extension.installSpace != null && extension.installSpace.length() > 0) {
                        classpath = project.files('$JAVAPATH')
                        defaultJvmOpts = ['-D$LD_LIBRARY_PATH']
                    }
                }

                task.doLast {
                    unixScript.text = unixScript.text.replace('$APP_HOME/lib/', '')
                    windowsScript.text = windowsScript.text.replace('$APP_HOME\\lib\\', '')
                }

                project.tasks.getByPath('amentInstall').dependsOn task
            }
        }
    }

    RclJavaPluginExtension loadExtension(Project project) {
        RclJavaPluginExtension extension = project.ament

        project.getLogger().lifecycle("eclipse : " + extension.generateEclipse)

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