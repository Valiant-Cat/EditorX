package editorx.core.plugin

import editorx.core.plugin.loader.PluginLoader
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Consumer


/**
 * 插件管理器
 * 负责插件的加载、卸载和生命周期管理
 */
class PluginManager {
    companion object {
        private val logger = LoggerFactory.getLogger(PluginManager::class.java)
    }

    private val allPlugins = TreeSet<PluginContextImpl>()
    private val addPluginListeners = ArrayList<Consumer<PluginContextImpl>>()

    fun loadPlugins(pluginLoader: PluginLoader) {
        allPlugins.clear()
        for (plugin in pluginLoader.load()) {
            addPlugin(plugin)
        }
    }

    private fun addPlugin(plugin: Plugin): PluginContextImpl {
        logger.info("Loading plugin: {}", plugin)
        val pluginContext = PluginContextImpl(plugin)
        require(allPlugins.add(pluginContext)) { "Duplicate plugin id: " + pluginContext + ", class " + plugin::class.java }
        addPluginListeners.forEach(Consumer { l: Consumer<PluginContextImpl> -> l.accept(pluginContext) })
        return pluginContext
    }

    fun getAllPluginContexts(): SortedSet<PluginContextImpl> {
        return allPlugins
    }

    fun registerAddPluginListener(listener: Consumer<PluginContextImpl>) {
        this.addPluginListeners.add(listener)
        // run for already added plugins
        getAllPluginContexts().forEach(listener)
    }

    fun activeAllPlugins() {
        for (pluginContext in allPlugins) {
            pluginContext.active()
        }
    }
}
