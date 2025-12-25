plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":i18n-keys"))
    
    implementation("com.fifesoft:rsyntaxtextarea:3.4.0")
    implementation("com.formdev:flatlaf:3.4")
    implementation("com.formdev:flatlaf-extras:3.4")
    
    // SLF4J 日志框架
    api(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
}
