import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version "2.3.0"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation("io.ktor:ktor-client-okhttp:2.3.9")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:2.3.9")
        }
        commonMain.dependencies {
            // Базовые библиотеки Compose
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(compose.materialIconsExtended)
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.1.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

            // Render the opaque QR payload returned by the API.
            implementation("io.github.alexzhirkevich:qrose:1.1.2")

            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            implementation("io.coil-kt.coil3:coil-network-ktor2:3.0.4")


            // Shared API client; the engine is supplied by each platform.
            implementation("io.ktor:ktor-client-core:2.3.9")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.9")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.9")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("io.ktor:ktor-client-mock:2.3.9")
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.testExt.junit)
            implementation("androidx.test:runner:1.7.0")
            implementation("androidx.compose.ui:ui-test-junit4:1.10.0")
            implementation("androidx.test:rules:1.7.0")
        }
    }
}

android {
    namespace = "kz.master_coffee.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    sourceSets.getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")

    defaultConfig {
        applicationId = "kz.master_coffee.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("masterCoffeeApiUrl").getOrElse("http://10.0.2.2:8080/api/v1")}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            val releaseUrl = providers.gradleProperty("masterCoffeeApiUrl").getOrElse("")
            buildConfigField("String", "API_BASE_URL", "\"${releaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.0")
}

val configuredReleaseApi = providers.gradleProperty("masterCoffeeApiUrl").getOrElse("")
val validateReleaseApiUrl = tasks.register("validateReleaseApiUrl") {
    inputs.property("apiUrl", configuredReleaseApi)
    val checkedUrl = configuredReleaseApi
    doLast {
        val uri = runCatching { URI(checkedUrl) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "Release requires -PmasterCoffeeApiUrl=https://YOUR-REAL-API-HOST/api/v1. No production endpoint is bundled."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" || it.name == "lintRelease" }.configureEach { dependsOn(validateReleaseApiUrl) }

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    minHeapSize = "32m"
    maxHeapSize = "128m"
    maxParallelForks = 1
    jvmArgs("-XX:ActiveProcessorCount=2", "-XX:+UseSerialGC")
}
