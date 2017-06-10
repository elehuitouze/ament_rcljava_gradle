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
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
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
class AndroidConfiguration extends CommonConfiguration {

    public void configure(final Project project) {
        super.configure(project)

        this.updateDependencies(project)
        this.configureInstallFiles(project)

        //Install files
        this.configureAndroid(project)
        this.updateAndroidSourceSet(project)
    }

    public void afterEvaluate(final Project project, RclJavaPluginExtension extension) {
        super.afterEvaluate(project, extension)
    }

    protected void updateSourceSet(Project project) {

    }

    /**
     * Change compiled sources output.
     **/
    protected void updateAndroidSourceSet(Project project) {
        if (project.ament.buildSpace != null) {
            project.plugins.withId(this.getAndroidPluginType(project)) {
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
            project.plugins.withId(this.getAndroidPluginType(project)) {
                project.dependencies {
                    project.ament.dependencies.split(':').each {
                        compile project.fileTree(
                            dir: new File(it, 'java'),
                            include: '*.jar',
                            excludes: ['slf4j-jdk*.jar', 'slf4j-log4j*.jar'])
                    }
                }
            }
        }
    }

    private void configureAndroid(Project project) {
        if (project.ament.buildSpace != null) {
            project.plugins.withId(this.getAndroidPluginType(project)) {
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
        project.plugins.withId(this.getAndroidPluginType(project)) {
            if (project.ament.buildSpace != null && project.ament.installSpace != null) {
                //TODO this not working with android plugin 3.0.0 anymore
                //https://developer.android.com/studio/preview/features/new-android-plugin-migration.html#variant_api
//                project.android.applicationVariants.all { variant ->
//                    variant.outputs.each { output ->
//                        def copyArtifacts = project.task('copyArtifacts' + variant.name.capitalize(), type: Copy) {
//                            from output.outputFile.absolutePath
//                            into project.ament.installSpace
//                        }
//
//                        assemble.finalizedBy copyArtifacts
//                    }
//                }
            }
        }
    }
    
    private String getAndroidPluginType(Project project) {
        return project.plugins.hasPlugin("com.android.application")
            ? "com.android.application"
            : "com.android.library";
    }
}
