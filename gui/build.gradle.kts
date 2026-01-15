plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    application
    // 使 gui 模块可以直接编写/嵌入 Compose Desktop（渐进式替换 Swing 组件）
    id("org.jetbrains.compose") version "1.8.2"
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

    implementation(libs.kotlinxSerialization)
    implementation("com.fifesoft:rsyntaxtextarea:3.4.0")
    implementation("com.formdev:flatlaf:3.4")
    implementation("com.formdev:flatlaf-extras:3.4")
    implementation(compose.desktop.currentOs)
    implementation(compose.material)

    // SLF4J（GUI 层也需要编译期依赖）
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("editorx.gui.GuiAppKt")
}

tasks.register<JavaExec>("runComposeDemo") {
    group = "application"
    description = "运行 Compose Desktop 示例入口（用于验证 Compose 支持）"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("editorx.gui.compose.ComposeDemoAppKt")
}

// 将仓库内置工具随 installDist / distZip 一并分发（macOS .app 打包也复用 installDist 目录）
distributions {
    main {
        contents {
            from(rootProject.file("tools")) {
                into("tools")
            }
        }
    }
}

// 禁用 distTar 任务，只生成 zip
tasks.named<Tar>("distTar") {
    enabled = false
}
