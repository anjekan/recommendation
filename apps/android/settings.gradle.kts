pluginManagement {
    repositories {
        google {
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RecommendationAndroid"
include(":app")
include(":core:domain")
include(":core:application")
include(":core:analysis")
include(":core:analysis-android")
include(":tools:model-contract")
include(":core:data-local")
include(":core:data-config")
include(":core:data-remote")
