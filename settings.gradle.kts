// =====================================================================================
// SalonLink — Gradle settings
// Declares where plugins and libraries are downloaded from, and which modules exist.
// =====================================================================================

pluginManagement {
    repositories {
        google {
            // Only look in Google's Maven repo for Google/Android artifacts (faster, safer).
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Modules may NOT declare their own repositories — everything is defined here.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SalonLink"
include(":app")
