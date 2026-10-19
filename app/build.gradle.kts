import java.io.FileInputStream
import java.util.Properties

// NOTE: This file was reconstructed after app/build.gradle.kts went missing
// (the cause of the ":app:assembleDebug not found" error). Values were recovered
// from the version catalog (gradle/libs.versions.toml), local.properties, and the
// last build's generated BuildConfig + merged manifest. The `release` build type's
// BASE_URL/flags are an inferred best-guess — confirm before shipping a release.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Dev backend config is read from local.properties at configuration time so the
// debug build can point at a local/tunnelled backend without code changes.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val baseUrl     = localProperties.getProperty("BASE_URL")      ?: "http://10.0.2.2:8000/"
val useMockApi  = localProperties.getProperty("USE_MOCK_API")  ?: "true"
val useMockAuth = localProperties.getProperty("USE_MOCK_AUTH") ?: "true"

android {
    namespace = "com.candorfoods.factoryops"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.candorfoods.factoryops"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            buildConfigField("String",  "BASE_URL",      "\"$baseUrl\"")
            buildConfigField("Boolean", "USE_MOCK_API",  useMockApi)
            buildConfigField("Boolean", "USE_MOCK_AUTH", useMockAuth)
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Inferred — original release values were not recoverable.
            buildConfigField("String",  "BASE_URL",      "\"$baseUrl\"")
            buildConfigField("Boolean", "USE_MOCK_API",  "false")
            buildConfigField("Boolean", "USE_MOCK_AUTH", "false")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.converter)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.work.runtime.ktx)
    implementation(libs.security.crypto)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.coil.compose)

    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.kotlinx.datetime)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    coreLibraryDesugaring(libs.desugar.jdk)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit5.engine)

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
