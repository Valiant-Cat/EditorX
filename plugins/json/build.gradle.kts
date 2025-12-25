plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.10.1")
}

