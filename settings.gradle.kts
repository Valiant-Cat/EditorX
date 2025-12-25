// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "EditorX"

include(":core")
include(":icons")
include(":gui")
include(":plugins:smali")
include(":plugins:json")
include(":plugins:yaml")
include(":plugins:xml")
include(":plugins:git")
include(":plugins:i18n-en")
include(":plugins:i18n-zh")
