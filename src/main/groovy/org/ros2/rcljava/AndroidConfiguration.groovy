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
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.plugins.ide.eclipse.EclipsePlugin

import com.google.common.io.Files

import com.android.build.gradle.AppPlugin

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
    }

    public void afterEvaluate(final Project project, RclJavaPluginExtension extension) {
        super.afterEvaluate(project, extension)
    }

    /**
     * Change compiled sources output.
     **/
    protected void updateSourceSet(Project project) {
        /*if (project.ament.buildSpace != null) {
            project.plugins.withType(com.android.build.gradle.AppPlugin) {
                project.android {
                    sourceSets {
                        main {
                            jniLibs.srcDirs = new File(project.ament.buildSpace, "jniLibs")
                        }
                    }
                }
            }
        }*/
    }

    private void updateDependencies(final Project project) {
        project.plugins.withType(com.android.build.gradle.AppPlugin) {
            project.dependencies {
                if (project.hasProperty('ament.dependencies')) {
                    project.getProperty('ament.dependencies').split(':').each {
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
        project.plugins.withType(com.android.build.gradle.AppPlugin) {
            if (project.hasProperty('ament.build_space') && project.hasProperty('ament.install_space')) {
                def buildSpaceDir = project.file(project.getProperty('ament.build_space'))

                project.buildDir = buildSpaceDir

                if (project.hasProperty('ament.android_stl')
                    && project.hasProperty('ament.android_abi')
                    && project.hasProperty('ament.android_ndk')) {

                    def androidSTL = project.getProperty('ament.android_stl')
                    def androidABI = project.getProperty('ament.android_abi')
                    def androidNDK = project.getProperty('ament.android_ndk')

                    def stlDestination = [buildSpaceDir, 'jniLibs', androidABI].join(File.separator)

                    project.task('cleanNativeLibs', type: Delete) {
                        delete "$stlDestination"
                    }

                    project.task('copyNativeLibs', type: Copy) {
                        project.getProperty('ament.dependencies').split(':').each {
                            def fp = [project.file(it).parentFile.parentFile, 'lib'].join(File.separator)
                            def ft = project.fileTree(dir: fp, include: '*.so')
                            from ft
                            into "$stlDestination"
                        }

                        // TODO(esteve): expand this to support other STL libraries
                        def stlLibraryPath = [
                            androidNDK, 'sources', 'cxx-stl', 'gnu-libstdc++', '4.9', 'libs', androidABI, 'lib' + androidSTL + '.so'
                        ].join(File.separator)

                        from stlLibraryPath
                    }

                    project.copyNativeLibs.dependsOn project.cleanNativeLibs

                    project.tasks.withType(JavaCompile) {
                        compileTask -> compileTask.dependsOn project.copyNativeLibs
                    }
                }
            }
        }
    }

    /**
     * Install files.
     **/
    private void configureInstallFiles(Project project) {
        project.plugins.withType(com.android.build.gradle.AppPlugin) {
            if (project.hasProperty('ament.build_space') && project.hasProperty('ament.install_space')) {
                def installSpaceDir = project.file(project.getProperty('ament.install_space'))
                def buildSpaceDir = project.file(project.getProperty('ament.build_space'))

                project.task('deployArtifacts') << {
                    project.copy {
                        description = "Copy artifacts to the install space"

                        from("$buildSpaceDir") {
                            include '**/*.apk'
                        }
                        into "$installSpaceDir"
                        includeEmptyDirs = false
                        eachFile {
                            details ->
                            details.path = details.file.name
                        }
                    }
                }

                project.build.finalizedBy project.deployArtifacts
            }
        }
    }
}
