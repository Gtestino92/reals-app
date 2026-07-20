package com.reals.app.ui.root

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FlavorBuildConfigTest {
    @Test
    fun `local firebase email auto verification build flag is flavor scoped`() {
        val gradleFile = File("build.gradle.kts").readText()

        assertTrue(
            gradleFile.contains(
                """create("local") {
            dimension = "environment"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("local"))
            buildConfigField("String", "REALS_BASE_URL", quoted(localBaseUrl))
            buildConfigField("boolean", "ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION", "true")""",
            ),
        )
        assertTrue(
            gradleFile.contains(
                """create("dev") {
            dimension = "environment"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("dev"))
            buildConfigField("String", "REALS_BASE_URL", quoted(devBaseUrl))
            buildConfigField("boolean", "ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION", "false")""",
            ),
        )
        assertTrue(
            gradleFile.contains(
                """create("prod") {
            dimension = "environment"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("prod"))
            buildConfigField("String", "REALS_BASE_URL", quoted(prodBaseUrl))
            buildConfigField("boolean", "ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION", "false")""",
            ),
        )
    }
}
