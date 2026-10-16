// Root Gradle settings for ThingSpot.
// Repositories are declared once here; modules may not add their own.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "thingspot"
include(":app")
include(":core:domain")
