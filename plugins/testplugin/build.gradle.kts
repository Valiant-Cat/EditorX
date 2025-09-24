plugins {
    kotlin("jvm") version "2.1.0"
}

group = "editor.plugins"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
}

kotlin {
    jvmToolchain(21)
}
