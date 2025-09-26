plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":plugins:smali"))

    implementation("com.fifesoft:rsyntaxtextarea:3.4.0")
    implementation("com.formdev:flatlaf:3.4")
    implementation("com.formdev:flatlaf-extras:3.4")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("editorx.gui.EditorGuiKt")
}
