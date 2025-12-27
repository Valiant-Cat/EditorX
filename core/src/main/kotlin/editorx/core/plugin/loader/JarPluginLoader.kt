package editorx.core.plugin.loader

import editorx.core.plugin.LoadedPlugin
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginOrigin
import org.slf4j.LoggerFactory
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*

/**
 * JAR 插件加载器
 * 从 plugins/ 目录加载 JAR 文件中的插件
 */
class JarPluginLoader : PluginLoader {

    companion object {
        private val logger = LoggerFactory.getLogger(JarPluginLoader::class.java)
    }

    override fun load(): List<LoadedPlugin> {
        val map = mutableMapOf<Class<out Plugin>, LoadedPlugin>()
        loadInstalledPlugins(map)
        return map.values.toList().sortedBy { it.plugin.javaClass.simpleName }
    }

    private fun loadInstalledPlugins(map: MutableMap<Class<out Plugin>, LoadedPlugin>) {
        val pluginDir = Path.of("plugins")
        pluginDir.toFile().listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "jar" }
            ?.forEach { jar -> loadFromJar(map, jar.toPath()) }
    }

    private fun loadFromJar(map: MutableMap<Class<out Plugin>, LoadedPlugin>, jar: Path) {
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

    private fun thisClassLoader() = this::class.java.classLoader
}

