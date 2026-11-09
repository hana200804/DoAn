import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val signaLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val signaApiBaseUrl = providers.gradleProperty("signa.apiBaseUrl")
    .orElse(providers.environmentVariable("SIGNA_API_BASE_URL"))
    .getOrElse(signaLocalProperties.getProperty("API_BASE_URL", "http://10.0.2.2:8080/api/v1/"))
val signaUseFakeBackend = providers.gradleProperty("signa.useFakeBackend")
    .map { it.toBooleanStrict() }.getOrElse(true)
val signaVietMapKey = providers.environmentVariable("VIETMAP_API_KEY")
    .getOrElse(signaLocalProperties.getProperty("VIETMAP_API_KEY", "")).trim()

val signaVietMapSearchKey = providers.environmentVariable("VIETMAP_SEARCH_API_KEY")
    .getOrElse(signaLocalProperties.getProperty("VIETMAP_SEARCH_API_KEY", "")).trim().ifBlank { signaVietMapKey }
val signaVietMapRouteKey = providers.environmentVariable("VIETMAP_ROUTE_API_KEY")
    .getOrElse(signaLocalProperties.getProperty("VIETMAP_ROUTE_API_KEY", "")).trim().ifBlank { signaVietMapSearchKey }

android {
    namespace = "com.signa.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        buildConfigField("String", "VIETMAP_API_KEY", "\"${signaVietMapKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "VIETMAP_SEARCH_API_KEY", "\"${signaVietMapSearchKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "VIETMAP_ROUTE_API_KEY", "\"${signaVietMapRouteKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${signaApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("boolean", "USE_FAKE_BACKEND", signaUseFakeBackend.toString())
        manifestPlaceholders["usesCleartextTraffic"] = "false"
        applicationId = "com.signa.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.vietmap.sdk)
    implementation(libs.vietmap.geojson)
    implementation(libs.vietmap.turf)
    implementation(libs.gson)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    androidTestImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
