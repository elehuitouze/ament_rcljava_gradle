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
package org.ros2.rcljava;

import java.io.File
import java.util.HashMap
import java.util.Map
import org.gradle.api.file.FileTree
import org.gradle.api.Project

class RclJavaPluginExtension {
    protected Project project
    
    def String dependencies = null
    def String buildSpace = null
    def String installSpace = null
    
    def String androidAbi = null
    def String androidNdk = null
    def String androidStl = null

    def boolean generateEclipse = false
    
    public FileTree[] getNativeDependencies() {
        def result = []
        
        if (this.dependencies != null) {
            this.dependencies.split(':').each {
                def libFolder = [new File(it).parentFile.parentFile, 'lib'].join(File.separator)
                def nativeLibs = this.project.fileTree(dir: libFolder, include: '*.so')
                result.add(nativeLibs)
            }
        }
        
        return result;
    }
}
