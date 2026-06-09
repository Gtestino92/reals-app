import java.util.Base64

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

val localBaseUrl = configValue("realsLocalBaseUrl", "REALS_LOCAL_BASE_URL", "http://10.0.2.2:8080/")
val devBaseUrl = configValue("realsDevBaseUrl", "REALS_DEV_BASE_URL", "https://api-dev.reals.example.com/")
val prodBaseUrl = configValue("realsProdBaseUrl", "REALS_PROD_BASE_URL", "https://api.reals.example.com/")

val releaseKeystore = releaseKeystoreFile()
val releaseStorePassword = providers.environmentVariable("REALS_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("REALS_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("REALS_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = releaseKeystore != null &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

if (
    listOf(
        "google-services.json",
        "src/local/google-services.json",
        "src/dev/google-services.json",
        "src/prod/google-services.json",
    ).any { file(it).exists() }
) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.reals.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.reals.app"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeValue
        versionName = versionNameValue

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["usesCleartextTraffic"] = "false"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            dimension = "environment"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("local"))
            buildConfigField("String", "REALS_BASE_URL", quoted(localBaseUrl))
        }
        create("dev") {
            dimension = "environment"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("dev"))
            buildConfigField("String", "REALS_BASE_URL", quoted(devBaseUrl))
        }
        create("prod") {
            dimension = "environment"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("String", "REALS_ENVIRONMENT", quoted("prod"))
            buildConfigField("String", "REALS_BASE_URL", quoted(prodBaseUrl))
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
