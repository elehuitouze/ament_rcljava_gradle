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

import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.application.tasks.CreateStartScripts

import java.util.jar.Attributes

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

        //Update dependencies
        this.updateDependencies(project, JavaPlugin.COMPILE_CONFIGURATION_NAME, "java")
        this.updateDependencies(project, JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME, "lib")

        this.updateEclipseClasspath(project)

        //Update sourceSet
        this.updateSourceSet(project)

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
     * Update project dependencies.
     * Add jar dependencies from ament configuration.
     **/
    private void updateDependencies(Project project, String configuration, String folder) {
        if (project.ament.dependencies != null) {
            project.ament.dependencies.split(':').each {
                def packagePath = new File(it)

                def mavenRepository = new File(packagePath, 'java/maven')

                if (mavenRepository.exists()) {
                    project.repositories {
                        maven {
                            url { 'file://' + mavenRepository.absolutePath }
                        }
                    }

                    def jarLibrary = packagePath.name

                    project.dependencies {
                        implementation(name: "$jarLibrary", version: '1.0.0') {
                            transitive = true
                        }
                    }
                } else {
                    project.dependencies {
                        implementation project.fileTree(
                                dir: new File(packagePath, 'java'),
                                include: '*.jar')
                    }
                }
            }
        }
    }

    /**
     * Update eclipse classpath file.
     * Add native folder to classpath.
     */
    private void updateEclipseClasspath(Project project) {
        if (project.ament.isGenerateEclipse()) {
            def nativePaths = "";

            project.ament.getNativeDependencies().each { it ->
                nativePaths += it.dir.absolutePath + "::"
            }

            if (nativePaths != "") {
                nativePaths = nativePaths.substring(0, nativePaths.size() - 2)

                project.eclipse.classpath.file.withXml { xml ->
                    def node = xml.asNode()
                    def container = node.find { it.@path.endsWith('rcljava.jar') }
                    container.appendNode('attributes').appendNode('attribute', [
                            name : 'org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY',
                            value: "$nativePaths"])

                }
            }
        }
    }

    /**
     * Change compiled sources output.
     **/
    private void updateSourceSet(Project project) {
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
                    test {
                        output.classesDir = new File(
                            project.ament.buildSpace,
                            "classes" + File.separator + SourceSet.TEST_SOURCE_SET_NAME)

                        output.resourcesDir = new File(
                            project.ament.buildSpace,
                            "resources" + File.separator + SourceSet.TEST_SOURCE_SET_NAME)
                    }
                }
            }
        }
    }

    /**
     * Pre-assemble files.
     **/
    protected void configurePrepareAssemble(Project project) {
        if (project.ament.buildSpace != null) {
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

                into new File(project.ament.buildSpace, "lib" + File.separator + "java")
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
        if (project.ament.buildSpace != null) {
            def install = project.tasks.create('amentInstall', Copy) {
                destinationDir new File(project.ament.buildSpace)

                into("share" + File.separator + project.name + File.separator + "java") {
                    from project.file("$project.buildDir/libs/")
                }

                into("lib" + File.separator + project.name) {
                    from project.file("$project.buildDir/scripts/")
                }
            }

            install.group = 'ament'
            install.description = 'Copy files to ament install folder'
            install.dependsOn 'jar'
            project.assemble.finalizedBy install

            project.getPluginManager().apply(MavenPlugin.class)

            def sourcesJar = project.task('androidSourcesJar', type: Jar) {
                classifier = 'sources'
                from project.sourceSets.main.java.srcDirs
            }

            project.artifacts {
                archives sourcesJar
            }

            project.uploadArchives {
                repositories {
                    mavenDeployer {
                        repository(url: "file://export"
                                + File.separator + project.ament.buildSpace
                                + File.separator + "share"
                                + File.separator + project.name
                                + File.separator + "java"
                                + File.separator + "maven")
                        pom.version = '1.0.0' //TODO set with package version
                        pom.groupId = "" //TODO handle groupId
                    }
                }
            }

            project.assemble.finalizedBy project.uploadArchives
        }
    }

    private void updateManifest(Project project) {
        project.tasks.withType(Jar) {
            manifest.attributes(
                (Attributes.Name.IMPLEMENTATION_TITLE.toString()) : project.archivesBaseName,
                (Attributes.Name.IMPLEMENTATION_VERSION.toString()): project.version,
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

                testLogging {
                    // set options for log level LIFECYCLE
                    events "passed", "skipped", "failed", "standardOut"
                    showExceptions true
                    exceptionFormat "full"
                    showCauses true
                    showStackTraces true

                    // set options for log level DEBUG and INFO
                    debug {
                        events "started", "passed", "skipped", "failed", "standardOut", "standardError"
                        exceptionFormat "full"
                    }
                    info.events = debug.events
                    info.exceptionFormat = debug.exceptionFormat

                    afterSuite { desc, result ->
                        if (!desc.parent) { // will match the outermost suite
                            def output = "Results: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} successes, ${result.failedTestCount} failures, ${result.skippedTestCount} skipped)"
                            def startItem = '|  ', endItem = '  |'
                            def repeatLength = startItem.length() + output.length() + endItem.length()
                            println('\n' + ('-' * repeatLength) + '\n' + startItem + output + endItem + '\n' + ('-' * repeatLength))
                        }
                    }
                }
            }
        }
    }

    private void configureCreateScript(final Project project) {
        if (this.extension.scripts != null) {
            for (NodeScript nodescript : this.extension.scripts) {
                def task = project.tasks.create('createScript' + nodescript.name, CreateStartScripts) {
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

                if (project.ament.buildSpace != null) {
                    project.tasks.getByPath('amentInstall').dependsOn task
                }
            }
        }
    }
}
