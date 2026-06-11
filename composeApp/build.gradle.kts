import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("com.google.gms.google-services")
    kotlin("plugin.serialization") version "1.9.20"
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
            implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
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
            implementation("io.github.onseok:peekaboo-image-picker:0.5.2")
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.1.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

            // Firebase (Авторизация и База данных)
            implementation("dev.gitlive:firebase-auth:1.11.1")
            implementation("dev.gitlive:firebase-firestore:1.11.1")
            // Модуль для работы с файлами в Firebase
            implementation("dev.gitlive:firebase-storage:1.11.1")
// Мощнейшая библиотека Kamel для асинхронной загрузки и кэширования картинок по ссылке
            implementation("media.kamel:kamel-image:0.9.4")

            // Генерация QR-кодов (ОСТАВЛЯЕМ ТОЛЬКО ЭТУ, НОВУЮ ВЕРСИЮ!)
            implementation("io.github.alexzhirkevich:qrose:1.1.2")

            implementation("io.coil-kt.coil3:coil-compose:3.0.0-rc01")
            implementation("io.coil-kt.coil3:coil-network-ktor2:3.0.0-rc01") // Для загрузки по сети


            // Ktor для отправки фоток в Cloudinary
            implementation("io.ktor:ktor-client-core:2.3.9")
            implementation("io.ktor:ktor-client-cio:2.3.9") // Универсальный движок
            implementation("io.ktor:ktor-client-content-negotiation:2.3.9")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.9")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "kz.master_coffee.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "kz.master_coffee.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}