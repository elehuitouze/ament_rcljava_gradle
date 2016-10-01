/* Copyright 2016 Open Source Robotics Foundation, Inc.
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

public class RclJavaPluginExtension {
    private String dependencies = null;
    private String buildSpace = null;
    private String installSpace = null;

    private boolean generateEclipse = false;

    public String getDependencies() {
        return this.dependencies;
    }
    public void setDependencies(String dependencies) {
        this.dependencies = dependencies;
    }

    public String getBuildSpace() {
        return this.buildSpace;
    }

    public void setBuildSpace(String buildSpace) {
        this.buildSpace = buildSpace;
    }

    public String getInstallSpace() {
        return this.installSpace;
    }

    public void setInstallSpace(String installSpace) {
        this.installSpace = installSpace;
    }

    public boolean isGenerateEclipse() {
        return this.generateEclipse;
    }

    public void setGenerateEclipse(boolean generateEclipse) {
        this.generateEclipse = generateEclipse;
    }
}
