plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":i18n-keys"))
    
    implementation("com.fifesoft:rsyntaxtextarea:3.4.0")
    implementation("com.formdev:flatlaf:3.4")
    implementation("com.formdev:flatlaf-extras:3.4")

    // 内置 JADX（反编译 DEX -> Java），避免依赖外部 jadx 环境
    implementation("io.github.skylot:jadx-core:1.5.2")
    runtimeOnly("io.github.skylot:jadx-dex-input:1.5.2")

    // dex2jar（DEX -> classes.jar），用于 AAR smali 修改后的回编译
    implementation("de.femtopedia.dex2jar:dex-tools:2.4.32")
    
    // SLF4J 日志框架
    api(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
}
