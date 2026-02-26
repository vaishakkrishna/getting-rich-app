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

    // Read local.properties for GitHub token
    val localProps = java.util.Properties()
    val localPropsFile = file("local.properties")
    if (localPropsFile.exists()) {
        localProps.load(localPropsFile.inputStream())
    }

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: localProps.getProperty("github_user")
                    ?: System.getenv("GITHUB_USER") ?: ""
                password = providers.gradleProperty("gpr.token").orNull
                    ?: localProps.getProperty("github_token")
                    ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "GettingRichApp"
include(":app")
include(":docs")
