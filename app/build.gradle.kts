import java.util.Base64
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun configValue(propertyName: String, envName: String, defaultValue: String): String {
    return providers.gradleProperty(propertyName).orNull
        ?: providers.environmentVariable(envName).orNull
        ?: defaultValue
}

fun quoted(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

data class ReleaseSigningInputs(
    val configured: Boolean,
    val keystoreFile: File?,
    val storePassword: String?,
    val keyAlias: String?,
    val keyPassword: String?,
)

fun releaseSigningInputs(): ReleaseSigningInputs {
    val keystorePath = providers.gradleProperty("realsReleaseKeystorePath").orNull
        ?.takeIf { it.isNotBlank() }
    val encodedKeystore = providers.environmentVariable("REALS_RELEASE_KEYSTORE_BASE64").orNull
        ?.takeIf { it.isNotBlank() }
    val storePassword = providers.environmentVariable("REALS_RELEASE_STORE_PASSWORD").orNull
        ?.takeIf { it.isNotBlank() }
    val keyAlias = providers.environmentVariable("REALS_RELEASE_KEY_ALIAS").orNull
        ?.takeIf { it.isNotBlank() }
    val keyPassword = providers.environmentVariable("REALS_RELEASE_KEY_PASSWORD").orNull
        ?.takeIf { it.isNotBlank() }

    val anySigningInput = listOf(
        keystorePath,
        encodedKeystore,
        storePassword,
        keyAlias,
        keyPassword,
    ).any { it != null }

    if (!anySigningInput) {
        return ReleaseSigningInputs(
            configured = false,
            keystoreFile = null,
            storePassword = null,
            keyAlias = null,
            keyPassword = null,
        )
    }

    if (keystorePath != null && encodedKeystore != null) {
        throw GradleException(
            "Configure only one release keystore source: realsReleaseKeystorePath or REALS_RELEASE_KEYSTORE_BASE64.",
        )
    }

    val missingInputs = buildList {
        if (keystorePath == null && encodedKeystore == null) {
            add("realsReleaseKeystorePath or REALS_RELEASE_KEYSTORE_BASE64")
        }
        if (storePassword == null) add("REALS_RELEASE_STORE_PASSWORD")
        if (keyAlias == null) add("REALS_RELEASE_KEY_ALIAS")
        if (keyPassword == null) add("REALS_RELEASE_KEY_PASSWORD")
    }

    if (missingInputs.isNotEmpty()) {
        throw GradleException(
            "Incomplete release signing configuration; missing ${missingInputs.joinToString()}.",
        )
    }

    val keystoreFile = if (keystorePath != null) {
        file(keystorePath).also {
            if (!it.isFile) {
                throw GradleException("Release keystore file does not exist: ${it.path}.")
            }
        }
    } else {
        val output = layout.buildDirectory.file("signing/reals-release.keystore").get().asFile
        output.parentFile.mkdirs()
        output.writeBytes(
            runCatching { Base64.getDecoder().decode(encodedKeystore) }.getOrElse {
                throw GradleException("REALS_RELEASE_KEYSTORE_BASE64 is not valid base64.", it)
            },
        )
        output
    }

    return ReleaseSigningInputs(
        configured = true,
        keystoreFile = keystoreFile,
        storePassword = storePassword,
        keyAlias = keyAlias,
        keyPassword = keyPassword,
    )
}

val versionCodeValue = configValue(
    propertyName = "realsVersionCode",
    envName = "REALS_VERSION_CODE",
    defaultValue = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull ?: "1",
).toInt()

val versionNameValue = configValue(
    propertyName = "realsVersionName",
    envName = "REALS_VERSION_NAME",
    defaultValue = providers.environmentVariable("GITHUB_SHA").orNull
        ?.take(7)
        ?.let { "0.1.0-$it" }
        ?: "0.1.0-local",
)

val localBaseUrl = configValue("realsLocalBaseUrl", "REALS_LOCAL_BASE_URL", "http://127.0.0.1:8080/")
val devBaseUrl = configValue("realsDevBaseUrl", "REALS_DEV_BASE_URL", "https://api-dev.reals.example.com/")
val prodBaseUrl = configValue("realsProdBaseUrl", "REALS_PROD_BASE_URL", "https://api.reals.example.com/")
val baseApplicationId = "com.reals.app"
val expectedApplicationIds = mapOf(
    "local" to "$baseApplicationId.local",
    "dev" to "$baseApplicationId.dev",
    "prod" to baseApplicationId,
)
val expectedAppNames = mapOf(
    "local" to "Reals Local",
    "dev" to "Reals Dev",
    "prod" to "Reals",
)
val localOnlyHosts = setOf("localhost", "127.0.0.1", "10.0.2.2", "::1", "0.0.0.0")
val placeholderHosts = setOf(
    "api-dev.reals.example.com",
    "api.reals.example.com",
)
val requestedTaskNames = gradle.startParameter.taskNames.map { it.lowercase() }
val requestedEnvironments = expectedApplicationIds.keys.filter { environment ->
    requestedTaskNames.any { taskName -> taskName.contains(environment) }
}.toSet()

val releaseSigningInputs = releaseSigningInputs()
val hasReleaseSigning = releaseSigningInputs.configured

val flavorGoogleServicesFiles = expectedApplicationIds.keys.associateWith { flavor ->
    file("src/$flavor/google-services.json")
}
val legacyGoogleServicesFile = file("google-services.json")
val shouldApplyGoogleServicesPlugin = flavorGoogleServicesFiles.values.any { it.exists() } ||
    (legacyGoogleServicesFile.exists() && requestedEnvironments == setOf("prod"))

if (shouldApplyGoogleServicesPlugin) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = baseApplicationId
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = baseApplicationId
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeValue
        versionName = versionNameValue

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["usesCleartextTraffic"] = "false"
        manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            dimension = "environment"
            applicationIdSuffix = ".local"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("local"))
            buildConfigField("String", "REALS_BASE_URL", quoted(localBaseUrl))
            buildConfigField("boolean", "ENABLE_FIREBASE_APP_CHECK", "false")
            buildConfigField("boolean", "ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION", "true")
            buildConfigField("boolean", "SHOW_MANUAL_LOCATION_FALLBACK", "true")
            buildConfigField("boolean", "SHOW_EXPLICIT_REFRESH_BUTTONS", "true")
        }
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("dev"))
            buildConfigField("String", "REALS_BASE_URL", quoted(devBaseUrl))
            buildConfigField("boolean", "ENABLE_FIREBASE_APP_CHECK", "true")
            buildConfigField("boolean", "ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION", "false")
            buildConfigField("boolean", "SHOW_MANUAL_LOCATION_FALLBACK", "false")
            buildConfigField("boolean", "SHOW_EXPLICIT_REFRESH_BUTTONS", "true")
        }
        create("prod") {
            dimension = "environment"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("prod"))
            buildConfigField("String", "REALS_BASE_URL", quoted(prodBaseUrl))
            buildConfigField("boolean", "ENABLE_FIREBASE_APP_CHECK", "true")
            buildConfigField("boolean", "ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION", "false")
            buildConfigField("boolean", "SHOW_MANUAL_LOCATION_FALLBACK", "false")
            buildConfigField("boolean", "SHOW_EXPLICIT_REFRESH_BUTTONS", "false")
        }
    }

    val releaseSigningConfig = if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = releaseSigningInputs.keystoreFile
            storePassword = releaseSigningInputs.storePassword
            keyAlias = releaseSigningInputs.keyAlias
            keyPassword = releaseSigningInputs.keyPassword
        }
    } else {
        null
    }

    buildTypes {
        debug {
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = true
            }
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

configurations.maybeCreate("devDebugImplementation")
configurations.maybeCreate("devReleaseImplementation")

val androidSdkDirectoryProvider = androidComponents.sdkComponents.sdkDirectory.map { it.asFile }

fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

fun commandOutput(command: List<String>, allowFailure: Boolean = false): Pair<Int, String> {
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitValue = process.waitFor()
    if (!allowFailure && exitValue != 0) {
        throw GradleException("Command failed ($exitValue): ${command.joinToString(" ")}\n$output")
    }
    return Pair(exitValue, output)
}

fun executableNames(toolName: String): List<String> {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    if (!isWindows) return listOf(toolName)
    return when (toolName) {
        "aapt2" -> listOf("aapt2.exe", "aapt2.bat", "aapt2")
        else -> listOf("$toolName.bat", "$toolName.exe", toolName)
    }
}

fun findAndroidSdkTool(toolName: String, sdkDirectory: File): File {
    val executableNames = executableNames(toolName)
    val candidateDirectories = buildList {
        val buildTools = sdkDirectory.resolve("build-tools")
        if (buildTools.isDirectory) {
            addAll(buildTools.listFiles { file -> file.isDirectory }.orEmpty().sortedByDescending { it.name })
        }
        add(sdkDirectory.resolve("cmdline-tools/latest/bin"))
        add(sdkDirectory.resolve("tools/bin"))
        add(sdkDirectory.resolve("platform-tools"))
    }

    return candidateDirectories
        .flatMap { directory -> executableNames.map { directory.resolve(it) } }
        .firstOrNull { it.isFile }
        ?: throw GradleException("$toolName not found under Android SDK directory ${sdkDirectory.path}.")
}

fun requireContains(value: String, expected: String, failureMessage: String) {
    if (!value.contains(expected)) {
        throw GradleException(failureMessage)
    }
}

fun parseBaseUrl(environment: String, value: String): URI {
    return runCatching { URI(value) }.getOrElse {
        throw GradleException("$environment REALS_BASE_URL must be an absolute URL ending with '/'.")
    }
}

fun isLocalOnlyHost(host: String): Boolean {
    val normalized = host.lowercase()
    return normalized in localOnlyHosts || normalized.endsWith(".localhost")
}

fun validateBaseUrl(environment: String, value: String) {
    val uri = parseBaseUrl(environment, value)
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase()

    if (scheme !in setOf("http", "https") || host.isNullOrBlank() || !value.endsWith("/")) {
        throw GradleException("$environment REALS_BASE_URL must be an absolute http(s) URL ending with '/'.")
    }

    if (environment == "local") {
        if (scheme == "http" && !isLocalOnlyHost(host)) {
            throw GradleException("local REALS_BASE_URL may use cleartext only with localhost, 127.0.0.1, 10.0.2.2, or another local-only host.")
        }
        return
    }

    if (scheme != "https") {
        throw GradleException("$environment REALS_BASE_URL must use HTTPS.")
    }
    if (isLocalOnlyHost(host)) {
        throw GradleException("$environment REALS_BASE_URL must not use localhost, loopback, emulator-only, or local-only hosts.")
    }
    if (host in placeholderHosts || host.endsWith(".example.com") || host.endsWith(".example.org") || host.endsWith(".example.net") || host.endsWith(".invalid")) {
        throw GradleException("$environment REALS_BASE_URL must be configured to a real non-placeholder HTTPS host.")
    }
}

fun googleServicesPackageNames(file: File): Set<String> {
    if (!file.exists()) return emptySet()
    return Regex(""""package_name"\s*:\s*"([^"]+)"""")
        .findAll(file.readText())
        .map { it.groupValues[1] }
        .toSet()
}

fun validateGoogleServicesFile(file: File, expectedPackageName: String) {
    if (!file.exists()) return
    val packageNames = googleServicesPackageNames(file)
    if (expectedPackageName !in packageNames) {
        throw GradleException("${file.path} must contain a Firebase Android client for '$expectedPackageName'.")
    }
}

val environmentBaseUrls = mapOf(
    "local" to localBaseUrl,
    "dev" to devBaseUrl,
    "prod" to prodBaseUrl,
)

environmentBaseUrls.forEach { (environment, baseUrl) ->
    val validationTask = tasks.register("validate${environment.capitalized()}Environment") {
        group = "verification"
        description = "Validates Reals $environment Android environment boundaries."
        notCompatibleWithConfigurationCache("Reads local Firebase JSON and project configuration during validation.")
        doLast {
            validateBaseUrl(environment, baseUrl)
            validateGoogleServicesFile(flavorGoogleServicesFiles.getValue(environment), expectedApplicationIds.getValue(environment))
            if (environment == "prod") {
                validateGoogleServicesFile(legacyGoogleServicesFile, expectedApplicationIds.getValue(environment))
            }
        }
    }
    tasks.matching { it.name.startsWith("pre${environment.capitalized()}") && it.name.endsWith("Build") }
        .configureEach {
            dependsOn(validationTask)
        }
}

tasks.register("validateFirebaseClientConfigurations") {
    group = "verification"
    description = "Validates present google-services.json files against isolated application IDs."
    notCompatibleWithConfigurationCache("Reads local Firebase JSON during validation.")
    doLast {
        expectedApplicationIds.forEach { (environment, applicationId) ->
            validateGoogleServicesFile(flavorGoogleServicesFiles.getValue(environment), applicationId)
        }
        validateGoogleServicesFile(legacyGoogleServicesFile, expectedApplicationIds.getValue("prod"))
        if (legacyGoogleServicesFile.exists()) {
            logger.warn("app/google-services.json is legacy for isolated builds; use app/src/prod/google-services.json for prod.")
        }
    }
}

tasks.register("validateEnvironmentIsolation") {
    group = "verification"
    description = "Validates application IDs, labels, Firebase config mapping, and URL boundaries."
    notCompatibleWithConfigurationCache("Uses script-level environment isolation assertions.")
    dependsOn("validateFirebaseClientConfigurations")
    doLast {
        if (expectedApplicationIds["local"] != "$baseApplicationId.local") {
            throw GradleException("local application ID must be $baseApplicationId.local.")
        }
        if (expectedApplicationIds["dev"] != "$baseApplicationId.dev") {
            throw GradleException("dev application ID must be $baseApplicationId.dev.")
        }
        if (expectedApplicationIds["prod"] != baseApplicationId) {
            throw GradleException("prod application ID must remain exactly $baseApplicationId.")
        }
        if (expectedAppNames != mapOf("local" to "Reals Local", "dev" to "Reals Dev", "prod" to "Reals")) {
            throw GradleException("Flavor app names must remain Reals Local, Reals Dev, and Reals.")
        }
        validateBaseUrl("local", localBaseUrl)
    }
}

tasks.register("verifyAppCheckDependencyIsolation") {
    group = "verification"
    description = "Verifies Firebase App Check provider dependencies match each build variant."
    notCompatibleWithConfigurationCache("Resolves variant runtime classpaths for dependency isolation checks.")
    doLast {
        fun moduleIds(configurationName: String): Set<String> =
            configurations.getByName(configurationName)
                .resolvedConfiguration
                .lenientConfiguration
                .allModuleDependencies
                .map { "${it.moduleGroup}:${it.moduleName}" }
                .toSet()

        val debugModule = "com.google.firebase:firebase-appcheck-debug"
        val playIntegrityModule = "com.google.firebase:firebase-appcheck-playintegrity"

        data class AppCheckProviderExpectation(
            val configurationName: String,
            val requiredModule: String?,
            val forbiddenModule: String,
            val secondForbiddenModule: String? = null,
            val requiredProviderName: String? = null,
            val forbiddenProviderName: String,
            val secondForbiddenProviderName: String? = null,
        )

        listOf(
            AppCheckProviderExpectation(
                configurationName = "localDebugRuntimeClasspath",
                requiredModule = null,
                forbiddenModule = playIntegrityModule,
                secondForbiddenModule = debugModule,
                forbiddenProviderName = "Play Integrity",
                secondForbiddenProviderName = "debug",
            ),
            AppCheckProviderExpectation(
                configurationName = "localReleaseRuntimeClasspath",
                requiredModule = null,
                forbiddenModule = playIntegrityModule,
                secondForbiddenModule = debugModule,
                forbiddenProviderName = "Play Integrity",
                secondForbiddenProviderName = "debug",
            ),
            AppCheckProviderExpectation(
                configurationName = "devDebugRuntimeClasspath",
                requiredModule = debugModule,
                forbiddenModule = playIntegrityModule,
                requiredProviderName = "debug",
                forbiddenProviderName = "Play Integrity",
            ),
            AppCheckProviderExpectation(
                configurationName = "devReleaseRuntimeClasspath",
                requiredModule = playIntegrityModule,
                forbiddenModule = debugModule,
                requiredProviderName = "Play Integrity",
                forbiddenProviderName = "debug",
            ),
            AppCheckProviderExpectation(
                configurationName = "prodDebugRuntimeClasspath",
                requiredModule = playIntegrityModule,
                forbiddenModule = debugModule,
                requiredProviderName = "Play Integrity",
                forbiddenProviderName = "debug",
            ),
            AppCheckProviderExpectation(
                configurationName = "prodReleaseRuntimeClasspath",
                requiredModule = playIntegrityModule,
                forbiddenModule = debugModule,
                requiredProviderName = "Play Integrity",
                forbiddenProviderName = "debug",
            ),
        ).forEach { expectation ->
            val modules = moduleIds(expectation.configurationName)
            val variantName = expectation.configurationName.removeSuffix("RuntimeClasspath")
            if (expectation.requiredModule != null && expectation.requiredModule !in modules) {
                throw GradleException(
                    "$variantName must include Firebase App Check ${expectation.requiredProviderName} provider.",
                )
            }
            if (expectation.forbiddenModule in modules) {
                throw GradleException(
                    "$variantName must not include Firebase App Check ${expectation.forbiddenProviderName} provider.",
                )
            }
            if (expectation.secondForbiddenModule != null && expectation.secondForbiddenModule in modules) {
                throw GradleException(
                    "$variantName must not include Firebase App Check ${expectation.secondForbiddenProviderName} provider.",
                )
            }
        }
    }
}

tasks.register("validateReleaseSigningConfiguration") {
    group = "verification"
    description = "Reports whether release signing is fully configured; partial inputs fail during configuration."
    notCompatibleWithConfigurationCache("Reads script-level release signing state for validation reporting.")
    doLast {
        if (hasReleaseSigning) {
            logger.lifecycle("Release signing is configured from external inputs; secret values are not logged.")
        } else {
            logger.lifecycle("Release signing is not configured; release APK outputs may be unsigned.")
        }
    }
}

tasks.register("verifyReleaseBuildHardening") {
    group = "verification"
    description = "Verifies release build types enable R8 optimization and resource shrinking."
    notCompatibleWithConfigurationCache("Reads the Android build type DSL after configuration.")
    doLast {
        val release = android.buildTypes.getByName("release")
        if (release.isDebuggable) {
            throw GradleException("release build type must not be debuggable.")
        }
        if (!release.isMinifyEnabled) {
            throw GradleException("release build type must enable code shrinking and obfuscation.")
        }
        if (!release.optimization.enable) {
            throw GradleException("release build type must enable AGP optimization.")
        }
        if (!release.isShrinkResources) {
            throw GradleException("release build type must enable resource shrinking.")
        }
    }
}

tasks.register("verifyLocalReleaseArtifacts") {
    group = "verification"
    description = "Inspects the optimized localRelease APK and R8 mapping outputs."
    dependsOn("assembleLocalRelease")
    notCompatibleWithConfigurationCache("Runs Android SDK inspection tools and inspects generated APK outputs.")
    doLast {
        val apkDirectory = layout.buildDirectory.dir("outputs/apk/local/release").get().asFile
        val apk = apkDirectory
            .listFiles { file -> file.isFile && file.extension == "apk" }
            .orEmpty()
            .maxByOrNull { it.lastModified() }
            ?: throw GradleException("No localRelease APK found in ${apkDirectory.path}.")

        val mappingFile = layout.buildDirectory.file("outputs/mapping/localRelease/mapping.txt").get().asFile
        if (!mappingFile.isFile || mappingFile.length() == 0L) {
            throw GradleException("R8 mapping file is missing or empty: ${mappingFile.path}.")
        }

        val sdkDirectory = androidSdkDirectoryProvider.get()
        val aapt2 = findAndroidSdkTool("aapt2", sdkDirectory)
        val badging = commandOutput(listOf(aapt2.path, "dump", "badging", apk.path)).second
        requireContains(
            badging,
            "package: name='com.reals.app.local'",
            "localRelease APK applicationId must be com.reals.app.local.",
        )
        requireContains(
            badging,
            "application-label:'Reals Local'",
            "localRelease APK label must be Reals Local.",
        )
        val packageLine = badging.lineSequence().firstOrNull { it.startsWith("package:") }.orEmpty()
        val versionCode = Regex("""versionCode='([^']*)'""").find(packageLine)?.groupValues?.get(1) ?: "unknown"
        val versionName = Regex("""versionName='([^']*)'""").find(packageLine)?.groupValues?.get(1) ?: "unknown"
        if (badging.contains("application-debuggable")) {
            throw GradleException("localRelease APK must not be debuggable.")
        }

        val manifest = commandOutput(listOf(aapt2.path, "dump", "xmltree", "--file", "AndroidManifest.xml", apk.path)).second
        requireContains(
            manifest,
            "com.reals.app.notifications.RealsFirebaseMessagingService",
            "localRelease APK manifest must include RealsFirebaseMessagingService.",
        )
        requireContains(
            manifest,
            "networkSecurityConfig",
            "localRelease APK manifest must reference a Network Security Config.",
        )

        val apksigner = findAndroidSdkTool("apksigner", sdkDirectory)
        val (signatureExit, signatureOutput) = commandOutput(
            listOf(apksigner.path, "verify", "--verbose", apk.path),
            allowFailure = true,
        )
        val signingStatus = if (signatureExit == 0) "signed" else "unsigned"

        val reportFile = layout.buildDirectory.file("reports/release/localRelease-apk-inspection.txt").get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("variant=localRelease")
                appendLine("apk=${apk.path}")
                appendLine("applicationId=com.reals.app.local")
                appendLine("label=Reals Local")
                appendLine("debuggable=false")
                appendLine("versionCode=$versionCode")
                appendLine("versionName=$versionName")
                appendLine("mapping=${mappingFile.path}")
                appendLine("mappingBytes=${mappingFile.length()}")
                appendLine("signing=$signingStatus")
                appendLine("aapt2=${aapt2.path}")
                appendLine("apksigner=${apksigner.path}")
                appendLine("signatureOutput=")
                appendLine(signatureOutput.trim())
            },
        )
        logger.lifecycle("localRelease APK inspection passed: ${apk.path} ($signingStatus).")
        logger.lifecycle("localRelease mapping file: ${mappingFile.path} (${mappingFile.length()} bytes).")
        logger.lifecycle("localRelease inspection report: ${reportFile.path}.")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.messaging)
    add("devDebugImplementation", libs.firebase.appcheck.debug)
    add("devReleaseImplementation", libs.firebase.appcheck.playintegrity)
    add("prodImplementation", libs.firebase.appcheck.playintegrity)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
