plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        java {
            srcDirs("src/main/kotlin", "src/main/java")
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation("com.fifesoft:rsyntaxtextarea:3.4.0")
    implementation("com.formdev:flatlaf:3.4")
    implementation("com.formdev:flatlaf-extras:3.4")
}
