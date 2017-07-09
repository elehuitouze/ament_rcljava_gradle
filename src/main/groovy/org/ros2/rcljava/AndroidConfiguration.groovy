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
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Jar

/**
 * Configures an Android Java ros2 project.
 *
 * - apply java plugin to project
 * - move aar to ament install space
 *
 * @author Erwan Le Huitouze <erwan.lehuitouze@gmail.com>
 */
class AndroidConfiguration extends CommonConfiguration {

    void configure(final Project project) {
        super.configure(project)

        this.updateDependencies(project)
        this.configureInstallFiles(project)

        //Install files
        this.configureAndroid(project)
        this.updateAndroidSourceSet(project)
    }

    void afterEvaluate(final Project project, RclJavaPluginExtension extension) {
        super.afterEvaluate(project, extension)
    }

    /**
     * Change compiled sources output.
     **/
    protected void updateAndroidSourceSet(Project project) {
        if (project.ament.buildSpace != null) {
            project.plugins.withId(getAndroidPluginType(project)) {
                project.android {
                    sourceSets {
                        main {
                            jniLibs.srcDirs = [[project.ament.buildSpace, 'jniLibs'].join(File.separator)]
                        }
                    }
                }
            }
        }
    }

    private void updateDependencies(final Project project) {
        if (project.ament.dependencies != null) {
            project.plugins.withId(getAndroidPluginType(project)) {
                project.dependencies {
                    compile group: 'org.slf4j', name: 'slf4j-android', version: '1.7.7'
                }
                
                def aarPath = null

                project.ament.dependencies.split(':').each {
                    def packagePath = new File(it)

                    def mavenRepository = new File(packagePath, 'java/maven')

                    if (mavenRepository.exists()) {
                        aarPath = it

                        project.repositories {
                            maven {
                                url { 'file://' + mavenRepository.absolutePath }
                            }
                        }

                        boolean hasAar = false

                        project.fileTree(dir: mavenRepository, include: '**/*.aar').each { file ->
                            hasAar = true;
                        }

                        def aarLibrary = packagePath.name
                        def extension = hasAar ? 'aar' : 'jar'

                        project.dependencies {
                            compile(name: "$aarLibrary", version: '1.0.0', ext: extension) {
                                transitive = true
                            }
                        }
                    } else {
                        def dependency = project.fileTree(
                                dir: new File(packagePath, 'java'),
                                include: '*.jar',
                                excludes: ['slf4j*.jar'])

                        if (isAndroidLibrary(project)) {
                            project.dependencies {
                                provided dependency
                            }
                        } else {
                            project.dependencies {
                                compile dependency
                            }
                        }
                    }
                }
            }
        }
    }

    private void configureAndroid(Project project) {
        if (project.ament.buildSpace != null) {
            project.plugins.withId(getAndroidPluginType(project)) {
                if (project.ament.androidStl != null) {
                    //this seem not working, see https://code.google.com/p/android/issues/detail?id=214664
                    //project.android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=$project.ament.androidStl")
                }
                
                if (project.ament.androidAbi != null) {
                    def nativeLibsDestination = [project.ament.buildSpace, 'jniLibs', project.ament.androidAbi].join(File.separator)

                    project.task('cleanNativeLibs', type: Delete) {
                        delete nativeLibsDestination
                    }
                    
                    project.task('copyNativeLibs', type: Copy) {
                        into nativeLibsDestination
                        
                        project.ament.getNativeDependencies().each {
                            from it
                        }
                    }

                    project.copyNativeLibs.dependsOn project.cleanNativeLibs
                    project.preBuild.dependsOn project.copyNativeLibs
                
                    if (project.ament.androidNdk != null && project.ament.androidStl != null) {
                        def stl = [project.ament.androidNdk, 'sources', 'cxx-stl', 'gnu-libstdc++', '4.9',
                                    'libs', project.ament.androidAbi, 'lib' + project.ament.androidStl + '.so'].join(File.separator)
                        
                        project.task('copyStlLib', type: Copy) {
                            into nativeLibsDestination
                            from stl
                        }
                        
                        project.copyNativeLibs.dependsOn project.copyStlLib
                    }
                }
            }
        }
    }

    /**
     * Install files.
     **/
    private void configureInstallFiles(Project project) {
        project.plugins.withId(getAndroidPluginType(project)) {
            if (project.ament.buildSpace != null && project.ament.installSpace != null) {
                //TODO this not working with android plugin 3.0.0 anymore
                //https://developer.android.com/studio/preview/features/new-android-plugin-migration.html#variant_api
                if (project.plugins.hasPlugin("com.android.application")) {
                    project.android.applicationVariants.all { variant ->
                        variant.outputs.each { output ->
                            def copyArtifacts = project.task('copyArtifacts' + variant.name.capitalize(), type: Copy) {
                                from output.outputFile.absolutePath
                                into project.ament.installSpace
                            }

                            assemble.finalizedBy copyArtifacts
                        }
                    }
                } else {
                    project.getPluginManager().apply(MavenPlugin.class)

                    def androidSourcesJar = project.task('androidSourcesJar', type: Jar) {
                        classifier = 'sources'
                        from project.android.sourceSets.main.java.srcDirs
                    }

                    project.artifacts {
                        archives androidSourcesJar
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
                            }
                        }
                    }

                    project.assemble.finalizedBy project.uploadArchives
                }
            }
        }
    }
    
    private static String getAndroidPluginType(Project project) {
        def type = "com.android.application"

        if (!project.plugins.hasPlugin(type)) {
            type = "com.android.library"
        }

        return type
    }

    private static boolean isAndroidLibrary(Project project) {
        return getAndroidPluginType(project).equals("com.android.library")
    }
}
