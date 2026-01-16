// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

import org.gradle.api.initialization.resolve.RepositoriesMode

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    // 统一在 settings 中管理仓库，避免因子模块覆盖导致 CI 拉取依赖失败。
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        // Compose Multiplatform / Desktop 依赖仓库
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "EditorX"

include(":core")
include(":icons")
include(":i18n-keys")
include(":gui")
include(":plugins:smali")
include(":plugins:json")
include(":plugins:yaml")
include(":plugins:xml")
include(":plugins:git")
include(":plugins:i18n-en")
include(":plugins:i18n-zh")
include(":plugins:apk")
include(":plugins:stringfog")
include(":plugins:aar")
