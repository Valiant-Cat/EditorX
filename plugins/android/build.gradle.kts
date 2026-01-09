plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":core"))
    implementation(project(":i18n-keys"))
    // 用于回显/预览 WebP 图标（ImageIO 插件）
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")

    // 用于 APK 签名（替代外部 apksigner，保证 .app 运行环境稳定）
    implementation("com.android.tools.build:apksig:8.2.2")
}
