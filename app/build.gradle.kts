import shadow.bundletool.com.android.tools.r8.internal.tR

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {

    namespace = "ru.alexgyver.GyverTwink"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "ru.alexgyver.GyverTwink"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

