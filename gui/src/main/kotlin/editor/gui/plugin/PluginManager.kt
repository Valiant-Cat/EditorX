package editor.gui.plugin

import editor.gui.MainFrame
import editor.plugin.Plugin
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * 插件管理器（GUI层，负责接入UI与插件）
 */
object PluginManager {
    private val logger = Logger.getLogger(PluginManager::class.java.name)
    private val plugins = ConcurrentHashMap<String, PluginInfo>()
    private val pluginLoaders = ConcurrentHashMap<String, URLClassLoader>()

    data class PluginInfo(
        val plugin: Plugin,
        val name: String,
        val version: String,
        val description: String,
        val jarFile: File,
        val loader: URLClassLoader?
    )

    fun loadPlugins() {
        logger.info("开始加载插件...")

        val pluginDir = File("plugins")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
            logger.info("插件目录不存在，已创建: ${pluginDir.absolutePath}")
        } else {
            val jarFiles = pluginDir.listFiles { file -> file.isFile && file.extension.lowercase() == "jar" }
            if (!jarFiles.isNullOrEmpty()) {
                logger.info("找到 ${jarFiles.size} 个外部插件文件")
                jarFiles.forEach { jarFile -> loadPlugin(jarFile) }
            }
        }

        logger.info("插件加载完成，共加载 ${plugins.size} 个插件")
    }

    private fun loadPlugin(jarFile: File) {
        try {
            logger.info("正在加载插件: ${jarFile.name}")

            val loader = URLClassLoader(arrayOf(jarFile.toURI().toURL()))

            val pluginClass = try {
                loader.loadClass("PluginMain")
            } catch (e: ClassNotFoundException) {
                logger.warning("插件 ${jarFile.name} 中未找到 PluginMain 类")
                return
            }

            val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin

            val pluginInfo = PluginInfo(
                plugin = plugin,
                name = getPluginName(pluginClass),
                version = getPluginVersion(pluginClass),
                description = getPluginDescription(pluginClass),
                jarFile = jarFile,
                loader = loader
            )

            val context = GuiPluginContext(
                MainFrame.instance.activityBar,
                MainFrame.instance.sideBar,
                MainFrame.instance.panel,
                MainFrame.instance.editor
            )

            plugin.activate(context)
            plugins[pluginInfo.name] = pluginInfo
            pluginLoaders[pluginInfo.name] = loader

            logger.info("插件加载成功: ${pluginInfo.name} v${pluginInfo.version}")
        } catch (e: Exception) {
            logger.severe("加载插件失败: ${jarFile.name}, 错误: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getPluginName(pluginClass: Class<*>): String =
        try {
            pluginClass.getDeclaredField("PLUGIN_NAME").get(null) as String
        } catch (_: Exception) {
            pluginClass.simpleName
        }

    private fun getPluginVersion(pluginClass: Class<*>): String =
        try {
            pluginClass.getDeclaredField("PLUGIN_VERSION").get(null) as String
        } catch (_: Exception) {
            "1.0.0"
        }

    private fun getPluginDescription(pluginClass: Class<*>): String =
        try {
            pluginClass.getDeclaredField("PLUGIN_DESCRIPTION").get(null) as String
        } catch (_: Exception) {
            "无描述"
        }

    fun unloadPlugin(pluginName: String) {
        plugins[pluginName]?.let { pluginInfo ->
            try {
                pluginInfo.loader?.close()
                plugins.remove(pluginName)
                pluginLoaders.remove(pluginName)
                logger.info("插件卸载成功: $pluginName")
            } catch (e: Exception) {
                logger.severe("卸载插件失败: $pluginName, 错误: ${e.message}")
            }
        }
    }
}

