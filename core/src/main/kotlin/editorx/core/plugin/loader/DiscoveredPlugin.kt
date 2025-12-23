package editorx.core.plugin.loader

import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginOrigin
import java.nio.file.Path

data class DiscoveredPlugin(
    val plugin: Plugin,
    val origin: PluginOrigin,
    val source: Path? = null,
    val classLoader: ClassLoader = plugin::class.java.classLoader,
    val closeable: AutoCloseable? = null,
)

