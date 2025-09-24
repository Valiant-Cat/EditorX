plugins {
    kotlin("jvm") version "2.1.0"
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

