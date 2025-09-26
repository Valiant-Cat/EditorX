package editorx.plugin

import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * 插件管理器
 * 负责插件的加载、卸载和生命周期管理
 */
class PluginManager(
    contextFactory: PluginContextFactory,
) {
    private val logger = Logger.getLogger(PluginManager::class.java.name)
    private val loadedPlugins = ConcurrentHashMap<String, LoadedPlugin>()

    // 插件加载器
    private val sourcePluginSanner = SourcePluginScanner(contextFactory)
    private val jarPluginSanner = JarPluginScanner(contextFactory)

    fun loadPlugins() {
        logger.info("开始加载插件...")

        // 使用源码插件加载器
        val sourcePlugins = sourcePluginSanner.scanPlugins()
        logger.info("源码插件加载完成，共加载 ${sourcePlugins.size} 个插件")
        sourcePlugins.forEach { loadedPlugin ->
            loadedPlugins[loadedPlugin.name] = loadedPlugin
        }

        // 使用JAR插件加载器
        val jarPlugins = jarPluginSanner.scanPlugins()
        logger.info("JAR插件加载完成，共加载 ${jarPlugins.size} 个插件")
        jarPlugins.forEach { loadedPlugin ->
            loadedPlugins[loadedPlugin.name] = loadedPlugin
        }

        logger.info("插件加载完成，共加载 ${loadedPlugins.size} 个插件")
    }

    fun listLoaded(): List<LoadedPlugin> = loadedPlugins.values.toList()

    fun unloadPlugin(pluginName: String) {
        loadedPlugins[pluginName]?.let { loadedPlugin ->
            try {
                runCatching { loadedPlugin.plugin.deactivate() }
                loadedPlugin.classLoader?.close()
                loadedPlugins.remove(pluginName)
                logger.info("插件卸载成功: $pluginName")
            } catch (e: Exception) {
                logger.severe("卸载插件失败: $pluginName, 错误: ${e.message}")
            }
        }
    }
}
