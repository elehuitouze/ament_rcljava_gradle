package org.ros2.rcljava

import org.gradle.testkit.runner.GradleRunner
import static org.junit.Assert.*

import org.junit.Ignore
import org.junit.Test

class AndroidProjectTest {

    def projectDir = new File(System.getProperty("user.dir") + "/testProjects/androidProject")
    def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
    def pluginClasspath = pluginClasspathResource.readLines().collect { new File(it) }

    void setUp() {
        def buildDir = new File(projectDir, "build")

        if(buildDir.exists()) buildDir.deleteDir()

        buildDir.delete()
        println pluginClasspathResource.readLines().join(":")
    }
//
//    void tearDown() {
//        setUp()
//    }

    @Ignore
    @Test
    public void testSimpleBuild() {
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath(pluginClasspath)
            .withArguments("build")
            .build()

//        assertEquals(UP_TO_DATE, result.task(":dealwithit").getOutcome())
//        assertTrue(result.output.contains("(•_•) ( •_•)>⌐■-■ (⌐■_■)"))

    }


}
