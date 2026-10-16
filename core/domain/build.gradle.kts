// Domain module: Places, Items, Categories, Labels and the rules between them.
// Pure Kotlin with no Android dependencies, so it can move to Kotlin Multiplatform later (ADR 0002).
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation("junit:junit:4.13.2")
}
