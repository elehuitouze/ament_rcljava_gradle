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

import org.junit.Test;
import org.kt3k.gradle.plugin.CoverallsPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;

import static org.junit.Assert.*;

public class RclJavaGradlePluginTest {

    @Test
    public void add_task_to_project() {
        boolean pluginIsApply = false;

        Project project = ProjectBuilder.builder().build();

        try {
            project.getPlugins().apply("org.ros2.rcljava");
            pluginIsApply = true;
        } catch (Exception e) {
            if (!(e instanceof java.lang.ClassNotFoundException)) {
                throw e;
            }
        }

        assertTrue("Plugin can't be apply, plugin class not found", pluginIsApply);
    }

    @Test
    public void check_apply_java_to_project() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("org.ros2.rcljava");

        assertTrue("Java plugin is not apply", project.getPlugins().hasPlugin(JavaPlugin.class));
        assertFalse("Eclipse plugin is apply", project.getPlugins().hasPlugin(EclipsePlugin.class));
    }

    @Test
    public void check_apply_eclipse_to_project() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("org.ros2.rcljava");

        assertTrue("Java plugin is not apply", project.getPlugins().hasPlugin(JavaPlugin.class));
//        assertTrue("Eclipse plugin is not apply", project.getPlugins().hasPlugin(EclipsePlugin.class));
    }

    @Test
    public void check_apply_report_to_project() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("org.ros2.rcljava");

        assertTrue("Jacoco plugin is not apply", project.getPlugins().hasPlugin(JacocoPlugin.class));
        assertTrue("Coveralls plugin is not apply", project.getPlugins().hasPlugin(CoverallsPlugin.class));
    }
}
