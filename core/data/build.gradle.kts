// Data module: stores the inventory on the device with Room (ADR 0001, ADR 0002).
// Nothing here talks to a network; the app has no INTERNET permission.
plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.bijojp.thingspot.data"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 26
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

// Exported schemas are committed so every future migration can be tested against them.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("junit:junit:4.13.2")
}
