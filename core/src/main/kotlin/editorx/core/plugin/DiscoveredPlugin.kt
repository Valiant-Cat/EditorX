package editorx.core.plugin

import java.nio.file.Path

data class DiscoveredPlugin(
    val plugin: Plugin,
    val origin: PluginOrigin,
    val source: Path? = null,
    val classLoader: ClassLoader = plugin::class.java.classLoader,
    val closeable: AutoCloseable? = null,
)
