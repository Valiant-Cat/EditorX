plugins {
    kotlin("jvm") version "2.1.0"
}

group = "com.xiaomao.tools"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
}

kotlin {
    jvmToolchain(21)
}

// 插件打包任务
tasks.register<Jar>("pluginJar") {
    archiveBaseName.set("explorer-plugin")
    archiveVersion.set("1.0.0")
    
    from(sourceSets.main.get().output)
    
    manifest {
        attributes(
            "Plugin-Name" to "Explorer",
            "Plugin-Version" to "1.0.0",
            "Plugin-Description" to "文件浏览器插件",
            "Plugin-Author" to "XiaMao Tools",
            "Main-Class" to "PluginMain"
        )
    }
}
