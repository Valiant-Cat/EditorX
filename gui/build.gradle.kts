plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":icons"))
    implementation(project(":i18n-keys"))
    implementation(project(":plugins:i18n-zh"))
    implementation(project(":plugins:i18n-en"))
    runtimeOnly(project(":plugins:android"))
    runtimeOnly(project(":plugins:stringfog"))
    implementation(project(":plugins:smali"))
    implementation(project(":plugins:json"))
    implementation(project(":plugins:yaml"))
    implementation(project(":plugins:xml"))
    implementation(project(":plugins:git"))

    implementation("com.fifesoft:rsyntaxtextarea:3.4.0")
    implementation("com.formdev:flatlaf:3.4")
    implementation("com.formdev:flatlaf-extras:3.4")

    // SLF4J（GUI 层也需要编译期依赖）
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("editorx.gui.GuiAppKt")
}

// 禁用 distTar 任务，只生成 zip
tasks.named<Tar>("distTar") {
    enabled = false
}
