package com.reals.app.ui.root

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FlavorBuildConfigTest {
    @Test
    fun `local firebase email auto verification build flag is flavor scoped`() {
        val gradleFile = appFile("build.gradle.kts").readText()

        assertTrue(flavorBlockHasEmailVerificationFlag(gradleFile, "local", "true"))
        assertTrue(flavorBlockHasEmailVerificationFlag(gradleFile, "dev", "false"))
        assertTrue(flavorBlockHasEmailVerificationFlag(gradleFile, "prod", "false"))
    }

    @Test
    fun `app check providers and dependencies are flavor scoped`() {
        val gradleFile = appFile("build.gradle.kts").readText()
        val localInstaller = appFile("src/local/java/com/reals/app/core/appcheck/AppCheckInstaller.kt").readText()
        val devInstaller = appFile("src/dev/java/com/reals/app/core/appcheck/AppCheckInstaller.kt").readText()
        val prodInstaller = appFile("src/prod/java/com/reals/app/core/appcheck/AppCheckInstaller.kt").readText()

        assertTrue(gradleFile.contains("""add("localImplementation", libs.firebase.appcheck.debug)"""))
        assertTrue(gradleFile.contains("""add("devImplementation", libs.firebase.appcheck.playintegrity)"""))
        assertTrue(gradleFile.contains("""add("prodImplementation", libs.firebase.appcheck.playintegrity)"""))
        assertTrue(localInstaller.contains("DebugAppCheckProviderFactory"))
        assertTrue(devInstaller.contains("PlayIntegrityAppCheckProviderFactory"))
        assertTrue(prodInstaller.contains("PlayIntegrityAppCheckProviderFactory"))
        assertTrue(!devInstaller.contains("DebugAppCheckProviderFactory"))
        assertTrue(!prodInstaller.contains("DebugAppCheckProviderFactory"))
    }

    private fun appFile(path: String): File {
        return listOf(File("app/$path"), File(path)).first { it.exists() }
    }

    private fun flavorBlockHasEmailVerificationFlag(
        gradleFile: String,
        flavor: String,
        enabled: String,
    ): Boolean {
        return Regex(
            """create\("$flavor"\)\s*\{[\s\S]*?buildConfigField\("boolean", "ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION", "$enabled"\)""",
        ).containsMatchIn(gradleFile)
    }
}
