package com.reals.app.ui.root

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FlavorBuildConfigTest {
    @Test
    fun `application ids and namespace are environment isolated`() {
        val gradleFile = appFile("build.gradle.kts").readText()

        assertTrue(gradleFile.contains("""namespace = baseApplicationId"""))
        assertTrue(gradleFile.contains("""applicationId = baseApplicationId"""))
        assertTrue(flavorBlockContains(gradleFile, "local", """applicationIdSuffix = ".local""""))
        assertTrue(flavorBlockContains(gradleFile, "dev", """applicationIdSuffix = ".dev""""))
        assertTrue(!flavorBlockContains(gradleFile, "prod", "applicationIdSuffix"))
        assertTrue(gradleFile.contains(""""prod" to baseApplicationId"""))
    }

    @Test
    fun `application labels are resource overlays per flavor`() {
        assertTrue(appFile("src/main/res/values/strings.xml").readText().contains("""<string name="app_name">Reals</string>"""))
        assertTrue(appFile("src/local/res/values/strings.xml").readText().contains("""<string name="app_name">Reals Local</string>"""))
        assertTrue(appFile("src/dev/res/values/strings.xml").readText().contains("""<string name="app_name">Reals Dev</string>"""))
    }

    @Test
    fun `cleartext network policy is local only`() {
        val manifest = appFile("src/main/AndroidManifest.xml").readText()
        val localNetworkConfig = appFile("src/local/res/xml/network_security_config.xml").readText()
        val devNetworkConfig = appFile("src/dev/res/xml/network_security_config.xml").readText()
        val prodNetworkConfig = appFile("src/prod/res/xml/network_security_config.xml").readText()

        assertTrue(manifest.contains("""android:networkSecurityConfig="${'$'}{networkSecurityConfig}""""))
        assertTrue(flavorBlockContains(appFile("build.gradle.kts").readText(), "local", """manifestPlaceholders["usesCleartextTraffic"] = "true""""))
        assertTrue(localNetworkConfig.contains("""<base-config cleartextTrafficPermitted="false" />"""))
        assertTrue(localNetworkConfig.contains("127.0.0.1"))
        assertTrue(localNetworkConfig.contains("10.0.2.2"))
        assertTrue(!devNetworkConfig.contains("""cleartextTrafficPermitted="true""""))
        assertTrue(!prodNetworkConfig.contains("""cleartextTrafficPermitted="true""""))
    }

    @Test
    fun `dev and prod base urls are validated against cleartext local and placeholder hosts`() {
        val gradleFile = appFile("build.gradle.kts").readText()

        assertTrue(gradleFile.contains("""if (scheme != "https")"""))
        assertTrue(gradleFile.contains("""isLocalOnlyHost(host)"""))
        assertTrue(gradleFile.contains("placeholderHosts"))
        assertTrue(gradleFile.contains("""${'$'}environment REALS_BASE_URL must be configured to a real non-placeholder HTTPS host."""))
    }

    @Test
    fun `local firebase email auto verification build flag is flavor scoped`() {
        val gradleFile = appFile("build.gradle.kts").readText()

        assertTrue(flavorBlockHasEmailVerificationFlag(gradleFile, "local", "true"))
        assertTrue(flavorBlockHasEmailVerificationFlag(gradleFile, "dev", "false"))
        assertTrue(flavorBlockHasEmailVerificationFlag(gradleFile, "prod", "false"))
    }

    @Test
    fun `app check providers and dependencies are variant scoped`() {
        val gradleFile = appFile("build.gradle.kts").readText()
        val localInstaller = appFile("src/local/java/com/reals/app/core/appcheck/AppCheckInstaller.kt").readText()
        val devDebugInstaller = appFile("src/devDebug/java/com/reals/app/core/appcheck/AppCheckInstaller.kt").readText()
        val devReleaseInstaller = appFile("src/devRelease/java/com/reals/app/core/appcheck/AppCheckInstaller.kt").readText()
        val prodInstaller = appFile("src/prod/java/com/reals/app/core/appcheck/AppCheckInstaller.kt").readText()

        assertTrue(gradleFile.contains("""add("localImplementation", libs.firebase.appcheck.debug)"""))
        assertTrue(gradleFile.contains("""add("devDebugImplementation", libs.firebase.appcheck.debug)"""))
        assertTrue(gradleFile.contains("""add("devReleaseImplementation", libs.firebase.appcheck.playintegrity)"""))
        assertTrue(gradleFile.contains("""add("prodImplementation", libs.firebase.appcheck.playintegrity)"""))
        assertTrue(localInstaller.contains("DebugAppCheckProviderFactory"))
        assertTrue(devDebugInstaller.contains("DebugAppCheckProviderFactory"))
        assertTrue(devReleaseInstaller.contains("PlayIntegrityAppCheckProviderFactory"))
        assertTrue(prodInstaller.contains("PlayIntegrityAppCheckProviderFactory"))
        assertTrue(!devDebugInstaller.contains("PlayIntegrityAppCheckProviderFactory"))
        assertTrue(!devReleaseInstaller.contains("DebugAppCheckProviderFactory"))
        assertTrue(!prodInstaller.contains("DebugAppCheckProviderFactory"))
        assertTrue(!File("app/src/dev/java/com/reals/app/core/appcheck/AppCheckInstaller.kt").exists())
        assertTrue(!File("src/dev/java/com/reals/app/core/appcheck/AppCheckInstaller.kt").exists())
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

    private fun flavorBlockContains(
        gradleFile: String,
        flavor: String,
        expected: String,
    ): Boolean {
        return Regex("""create\("$flavor"\)\s*\{[\s\S]*?${Regex.escape(expected)}""").containsMatchIn(gradleFile)
    }
}
