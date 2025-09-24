plugins {
    id("buildsrc.convention.kotlin-jvm")
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

// 配置标准jar任务
afterEvaluate {
    tasks.jar {
        archiveBaseName.set("explorer")
        archiveVersion.set("1.0.0")

        manifest {
            attributes(
                "Plugin-Name" to "Explorer",
                "Plugin-Desc" to "文件浏览器插件",
                "Plugin-Version" to "1.0.0",
                "Main-Class" to "editorx.plugins.explorer.ExplorerPlugin"
            )
        }
    }
}
