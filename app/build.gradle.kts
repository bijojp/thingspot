// App module build file.
// No Firebase, no Google Play Services, no analytics SDKs, no INTERNET permission.
// The only proprietary code allowed anywhere is Play Billing, in the play flavour (ADR 0004).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.bijojp.thingspot"
    // Current Compose needs API 37 to compile against; targetSdk below is what Play requires.
    compileSdk = 37
    // Pinned so F-Droid's build server uses the exact same tools (reproducible builds, ADR 0003).
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.bijojp.thingspot"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // fdroid: fully free code, Pro always unlocked. play: adds Play Billing for the Pro unlock.
    flavorDimensions += "store"
    productFlavors {
        create("fdroid") { dimension = "store" }
        create("play") { dimension = "store" }
    }

    buildTypes {
        debug {
            // Installs side by side with the release build so testing never touches real data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Deliberately unsigned: releases are signed outside Gradle with apksigner from
            // build-tools 34, the newest whose output F-Droid can verify (ADR 0003).
        }
    }

    // Never strip prebuilt native libraries: AGP strips them only when an NDK happens to be
    // installed, which would make F-Droid's build differ from ours (reproducible builds).
    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }

    // Google's encrypted dependency-metadata block makes F-Droid reject the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        // targetSdk stays at what Play requires (36) until we choose to adopt the next one.
        disable += "OldTargetApi"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    // Compose BOM keeps Compose artifacts aligned.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation3:navigation3-runtime:1.2.0")
    implementation("androidx.navigation3:navigation3-ui:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
