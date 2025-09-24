package editor.plugin

import java.io.File
import java.net.URLClassLoader

data class LoadedPlugin(
    val plugin: Plugin,
    val name: String,
    val version: String,
    val description: String,
    val jarFile: File,
    val loader: URLClassLoader?
)