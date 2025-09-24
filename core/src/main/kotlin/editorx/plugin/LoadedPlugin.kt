package editorx.plugin

import java.io.File
import java.net.URLClassLoader

data class LoadedPlugin(
    val plugin: Plugin,
    val name: String,
    val version: String,
    val description: String,
    val jarFile: File?, // 可以为null，支持源码插件
    val loader: URLClassLoader?
)
