plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation("com.fifesoft:rsyntaxtextarea:3.4.0")
    implementation("com.formdev:flatlaf:3.4")
    implementation("com.formdev:flatlaf-extras:3.4")

    testImplementation(kotlin("test"))
}
