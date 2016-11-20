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
class JavaConfiguration extends CommonConfiguration {

    public void afterEvaluate(final Project project, RclJavaPluginExtension extension) {
        super.afterEvaluate(project, extension)

        this.configurePrepareAssemble(project)

        //Install files
        this.configureInstallFiles(project)

        //Update jar manifest
        this.updateManifest(project)

        //Update test task
        this.updateTestTask(project)

        this.configureCreateScript(project)
    }

    /**
     * Pre-assemble files.
     **/
    protected void configurePrepareAssemble(Project project) {
        if (project.ament.installSpace != null) {
            def copy = project.tasks.create('prepareAssemble', Copy) {
                from(project.configurations.runtime) {
                    if (project.ament.dependencies != null) {
                        project.ament.dependencies.split(':').each {
                            project.fileTree(dir: new File(it, "java"), include: '*.jar').each { file ->
                                exclude file.name
                            }
                        }
                    }
                }
                from project.configurations.testRuntime {
                    if (project.ament.dependencies != null) {
                        project.ament.dependencies.split(':').each {
                            project.fileTree(dir: new File(it, "java"), include: '*.jar').each { file ->
                                exclude file.name
                            }
                        }
                    }
                }
                from 'lib'

                into new File(project.ament.installSpace, "lib" + File.separator + "java")
            }

            copy.group = 'build'
            copy.description = 'Copy libs used by ament in build space'
            project.tasks.getByPath('assemble').dependsOn copy
        }
    }

    /**
     * Install files.
     **/
    private void configureInstallFiles(Project project) {
        if (project.ament.installSpace != null) {
            def install = project.tasks.create('amentInstall', Copy) {
                destinationDir new File(project.ament.installSpace)

                into("share" + File.separator + project.name + File.separator + "java") {
                    from project.file('build/libs/')
                }

                into("bin") {
                    from project.file('build/scripts/')
                }
            }

            install.group = 'ament'
            install.description = 'Copy files to ament install folder'
            install.dependsOn 'jar'
            project.build.finalizedBy install
        }
    }

    private void updateManifest(Project project) {
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

    private void updateTestTask(Project project) {
        if (project.ament.buildSpace != null) {
            project.tasks.withType(Test) {
                def path = ""

                FileTree tree = project.fileTree(new File(project.ament.buildSpace))
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

    private void configureCreateScript(final Project project) {
        if (this.extension.scripts != null) {
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
}
