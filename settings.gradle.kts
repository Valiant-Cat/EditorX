plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "apk_editor"

include(":core")
include(":gui")
include(":plugins:explorer")
include(":plugins:testplugin")
