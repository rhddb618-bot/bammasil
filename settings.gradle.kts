pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google {
      content {
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
      }
    }
    mavenCentral()
    maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    maven("https://jitpack.io") { content { includeGroup("io.github.cosinekitty") } }
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Pleos Camping"

include(":app")
