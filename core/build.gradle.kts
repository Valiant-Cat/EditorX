plugins {
    id("buildsrc.convention.kotlin-jvm")
}

group = "com.xiaomao.tools"
version = "1.0.0"

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

