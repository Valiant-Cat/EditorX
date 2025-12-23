package editorx.core.plugin.loader

import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginOrigin
import org.slf4j.LoggerFactory
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*


class PluginLoaderImpl : PluginLoader {

    companion object {
        private val logger = LoggerFactory.getLogger(PluginLoaderImpl::class.java)
    }

    override fun load(): List<DiscoveredPlugin> {
        val map = mutableMapOf<Class<out Plugin>, DiscoveredPlugin>()

        loadFromClsLoader(map, thisClassLoader(), origin = PluginOrigin.CLASSPATH, source = null, closeable = null)
        loadInstalledPlugins(map)

        val list = map.values.toList().sortedBy { it.plugin.javaClass.simpleName }
        return list
    }

    private fun loadFromClsLoader(
        map: MutableMap<Class<out Plugin>, DiscoveredPlugin>,
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
                map[p.type()] = DiscoveredPlugin(
                    plugin = plugin,
                    origin = origin,
                    source = source,
                    classLoader = classLoader,
                    closeable = closeable,
                )
            }
    }

    private fun loadInstalledPlugins(map: MutableMap<Class<out Plugin>, DiscoveredPlugin>) {
        val pluginDir = Path.of("plugins")
        pluginDir.toFile().listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "jar" }
            ?.forEach { jar -> loadFromJar(map, jar.toPath()) }
    }

    private fun loadFromJar(map: MutableMap<Class<out Plugin>, DiscoveredPlugin>, jar: Path) {
        val jarFile = jar.toFile()
        val clsLoaderName = "editorx-plugin:" + jarFile.name
        val urls: Array<URL> = arrayOf(jarFile.toURI().toURL())
        val pluginClsLoader = URLClassLoader(clsLoaderName, urls, thisClassLoader())
        val beforeSize = map.size
        try {
            loadFromClsLoader(
                map,
                pluginClsLoader,
                origin = PluginOrigin.JAR,
                source = jar,
                closeable = pluginClsLoader,
            )
        } catch (e: Exception) {
            logger.warn("从 JAR 加载插件失败：{}", jar, e)
        } finally {
            // 若该 JAR 未发现任何插件，立即关闭 ClassLoader，避免泄漏
            if (map.size == beforeSize) {
                runCatching { pluginClsLoader.close() }
            }
        }
    }

    private fun thisClassLoader() = this::class.java.classLoader
}
