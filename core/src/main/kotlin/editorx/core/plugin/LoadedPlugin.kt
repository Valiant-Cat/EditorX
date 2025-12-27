package editorx.core.plugin

import java.nio.file.Path

data class LoadedPlugin(
    val plugin: Plugin,
    val origin: PluginOrigin,
    val path: Path? = null,
    val classLoader: ClassLoader = plugin::class.java.classLoader,
    val closeable: AutoCloseable? = null,
)
