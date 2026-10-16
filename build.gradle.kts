// Root build file for ThingSpot.
// Plugin versions live here so module build files stay small and readable.
// AGP 9+ has built-in Kotlin support for Android modules, so kotlin.android is not applied.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
}
