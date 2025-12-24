plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":core"))
}
