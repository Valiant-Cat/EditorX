plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

group = "com.xiaomao.tools"
version = "1.0.0"

dependencies {
    implementation(project(":core"))
    implementation(project(":plugins:explorer"))
    implementation(project(":plugins:testplugin"))

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
    mainClass.set("editorx.gui.EditorGuiKt")
}
