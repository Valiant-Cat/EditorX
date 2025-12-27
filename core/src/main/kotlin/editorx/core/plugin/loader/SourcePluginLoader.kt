package editorx.core.plugin.loader

import editorx.core.plugin.LoadedPlugin
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginOrigin
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*

/**
 * 源码插件加载器
 * 从 ClassPath 中加载插件（通过 ServiceLoader）
 */
class SourcePluginLoader : PluginLoader {

    companion object {
        private val logger = LoggerFactory.getLogger(SourcePluginLoader::class.java)
    }

    override fun load(): List<LoadedPlugin> {
        val map = mutableMapOf<Class<out Plugin>, LoadedPlugin>()
        val classLoader = this::class.java.classLoader
        loadFromClsLoader(map, classLoader, origin = PluginOrigin.SOURCE, source = null, closeable = null)
        return map.values.toList().sortedBy { it.plugin.javaClass.simpleName }
    }

    private fun loadFromClsLoader(
        map: MutableMap<Class<out Plugin>, LoadedPlugin>,
        classLoader: ClassLoader,
        origin: PluginOrigin,
        source: Path?,
        closeable: AutoCloseable?,
    ) {
        ServiceLoader.load(Plugin::class.java, classLoader)
            .stream()
            .filter { p: ServiceLoader.Provider<Plugin> -> p.type().classLoader === classLoader }
            .filter { p: ServiceLoader.Provider<Plugin> -> !map.containsKey(p.type()) }
            .forEach { p: ServiceLoader.Provider<Plugin> ->
                val plugin = runCatching { p.get() }.getOrElse { e ->
                    logger.warn("插件实例化失败：{} ({})", p.type().name, origin, e)
                    return@forEach
                }
                map[p.type()] = LoadedPlugin(
                    plugin = plugin,
                    origin = origin,
                    path = source,
                    classLoader = classLoader,
                    closeable = closeable,
                )
            }
    }
}

