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

fun releaseKeystoreFile(): File? {
    providers.gradleProperty("realsReleaseKeystorePath").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { return file(it) }

    val encoded = providers.environmentVariable("REALS_RELEASE_KEYSTORE_BASE64").orNull
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val output = layout.buildDirectory.file("signing/reals-release.keystore").get().asFile
    output.parentFile.mkdirs()
    output.writeBytes(Base64.getDecoder().decode(encoded))
    return output
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

val releaseKeystore = releaseKeystoreFile()
val releaseStorePassword = providers.environmentVariable("REALS_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("REALS_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("REALS_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = releaseKeystore != null &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

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
            buildConfigField("boolean", "ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION", "false")
            buildConfigField("boolean", "SHOW_MANUAL_LOCATION_FALLBACK", "true")
            buildConfigField("boolean", "SHOW_EXPLICIT_REFRESH_BUTTONS", "true")
        }
        create("prod") {
            dimension = "environment"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("prod"))
            buildConfigField("String", "REALS_BASE_URL", quoted(prodBaseUrl))
            buildConfigField("boolean", "ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION", "false")
            buildConfigField("boolean", "SHOW_MANUAL_LOCATION_FALLBACK", "false")
            buildConfigField("boolean", "SHOW_EXPLICIT_REFRESH_BUTTONS", "false")
        }
    }

    val releaseSigningConfig = if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = releaseKeystore
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else {
        null
    }

    buildTypes {
        debug {
        }
        release {
            optimization {
                enable = false
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

fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

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
    description = "Verifies Firebase App Check debug dependency does not leak into dev or prod."
    notCompatibleWithConfigurationCache("Resolves variant runtime classpaths for dependency isolation checks.")
    doLast {
        fun moduleIds(configurationName: String): Set<String> =
            configurations.getByName(configurationName)
                .resolvedConfiguration
                .lenientConfiguration
                .allModuleDependencies
                .map { "${it.moduleGroup}:${it.moduleName}" }
                .toSet()

        val localModules = moduleIds("localDebugRuntimeClasspath")
        val devModules = moduleIds("devDebugRuntimeClasspath")
        val devReleaseModules = moduleIds("devReleaseRuntimeClasspath")
        val prodDebugModules = moduleIds("prodDebugRuntimeClasspath")
        val prodModules = moduleIds("prodReleaseRuntimeClasspath")
        val debugModule = "com.google.firebase:firebase-appcheck-debug"
        val playIntegrityModule = "com.google.firebase:firebase-appcheck-playintegrity"

        if (debugModule !in localModules) {
            throw GradleException("localDebug must include Firebase App Check debug provider.")
        }
        if (playIntegrityModule !in devModules) {
            throw GradleException("devDebug must include Firebase App Check Play Integrity provider.")
        }
        if (playIntegrityModule !in devReleaseModules) {
            throw GradleException("devRelease must include Firebase App Check Play Integrity provider.")
        }
        if (playIntegrityModule !in prodDebugModules) {
            throw GradleException("prodDebug must include Firebase App Check Play Integrity provider.")
        }
        if (playIntegrityModule !in prodModules) {
            throw GradleException("prodRelease must include Firebase App Check Play Integrity provider.")
        }
        if (debugModule in devModules || debugModule in devReleaseModules || debugModule in prodDebugModules || debugModule in prodModules) {
            throw GradleException("Firebase App Check debug provider must not be present in dev or prod runtime classpaths.")
        }
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
    implementation(libs.firebase.messaging)
    add("localImplementation", libs.firebase.appcheck.debug)
    add("devImplementation", libs.firebase.appcheck.playintegrity)
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
