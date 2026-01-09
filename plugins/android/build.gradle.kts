plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":core"))
    implementation(project(":i18n-keys"))
    // 用于回显/预览 WebP 图标（ImageIO 插件）
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
}
