package editor.plugin

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.logging.Logger

/**
 * 插件管理器
 * 负责插件的加载、卸载和生命周期管理
 */
class PluginManager(private val contextFactory: PluginContextFactory) {
    private val logger = Logger.getLogger(PluginManager::class.java.name)
    private val plugins = ConcurrentHashMap<String, LoadedPlugin>()
    private val pluginLoaders = ConcurrentHashMap<String, URLClassLoader>()

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

            // 为插件创建独立类加载器，设置父加载器为应用类加载器
            val loader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this::class.java.classLoader)

            // 读取 Manifest 获取主类与元信息
            val manifest = JarFile(jarFile).use { it.manifest }
            val attrs = manifest?.mainAttributes
            val nameFromMf = attrs?.getValue("Plugin-Name")
            val descFromMf = attrs?.getValue("Plugin-Description") ?: attrs?.getValue("Plugin-Desc")
            val versionFromMf = attrs?.getValue("Plugin-Version")
            val mainClassName = attrs?.getValue("Main-Class") ?: "PluginMain"

            // 加载主类
            val pluginClass = try {
                loader.loadClass(mainClassName)
            } catch (e: ClassNotFoundException) {
                logger.warning("插件 ${jarFile.name} 中未找到主类: $mainClassName")
                return
            }

            val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin
            val pluginName = nameFromMf ?: pluginClass.simpleName
            val pluginVersion = versionFromMf ?: "1.0.0"
            val pluginDesc = descFromMf ?: ""

            val loadedPlugin = LoadedPlugin(
                plugin = plugin,
                name = pluginName,
                version = pluginVersion,
                description = pluginDesc,
                jarFile = jarFile,
                loader = loader
            )

            val context = contextFactory.createPluginContext(loadedPlugin)

            plugin.activate(context)
            plugins[loadedPlugin.name] = loadedPlugin
            pluginLoaders[loadedPlugin.name] = loader

            logger.info("插件加载成功: ${loadedPlugin.name} v${loadedPlugin.version}")
        } catch (e: Exception) {
            logger.severe("加载插件失败: ${jarFile.name}, 错误: ${e.message}")
            e.printStackTrace()
        }
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
