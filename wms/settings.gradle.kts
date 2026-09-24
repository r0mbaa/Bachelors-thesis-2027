pluginManagement {
    includeBuild("build-logic")
}

plugins {
    // Подкачивает JDK нужной версии, если на машине её нет (CI, чужой ноутбук на защите).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "wms"

include(
    "shared",
    "layout",
    "planner-engine",
    "planner",
    "core",
    "research",
)
