plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "com.xiaomao.tools"
version = "1.0.0"

dependencies {
    implementation(project(":core"))

    implementation("com.fifesoft:rsyntaxtextarea:3.4.0")
    implementation("com.formdev:flatlaf:3.4")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("AppKt")
}

