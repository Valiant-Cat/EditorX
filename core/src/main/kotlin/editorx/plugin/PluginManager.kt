package editorx.plugin

import editorx.event.EventBus
import editorx.event.PluginLoaded
import editorx.event.PluginUnloaded
import editorx.util.ClassScanner
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.logging.Logger

/**
 * 插件管理器
 * 负责插件的加载、卸载和生命周期管理
 */
class PluginManager(
    private val contextFactory: PluginContextFactory,
    private val eventBus: EventBus? = null
) {
    private val logger = Logger.getLogger(PluginManager::class.java.name)
    private val plugins = ConcurrentHashMap<String, LoadedPlugin>()
    private val pluginLoaders = ConcurrentHashMap<String, URLClassLoader>()

    fun loadPlugins() {
        logger.info("开始加载插件...")

        // 1. 加载JAR插件
        loadJarPlugins()

        // 2. 扫描并加载源码插件
        scanAndLoadSourcePlugins()

        logger.info("插件加载完成，共加载 ${plugins.size} 个插件")
    }

    fun listLoaded(): List<LoadedPlugin> = plugins.values.toList()

    /**
     * 加载JAR格式的插件
     */
    private fun loadJarPlugins() {
        val pluginDir = File("plugins")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
            logger.info("插件目录不存在，已创建: ${pluginDir.absolutePath}")
        } else {
            val jarFiles = pluginDir.listFiles { file -> file.isFile && file.extension.lowercase() == "jar" }
            if (!jarFiles.isNullOrEmpty()) {
                logger.info("找到 ${jarFiles.size} 个JAR插件文件")
                jarFiles.forEach { jarFile -> loadJarPlugin(jarFile) }
            }
        }
    }

    /**
     * 扫描并加载源码插件
     */
    private fun scanAndLoadSourcePlugins() {
        logger.info("开始扫描源码插件...")

        try {
            // 扫描多个包中的插件
            val packagesToScan = listOf(
                "editorx",
            )

            val allPluginClasses = mutableSetOf<Class<*>>()

            for (packageName in packagesToScan) {
                logger.info("扫描包: $packageName")
                val pluginClasses = ClassScanner.findSubclasses(packageName, Plugin::class.java)
                logger.info("在包 $packageName 中找到 ${pluginClasses.size} 个插件类")
                pluginClasses.forEach { clazz ->
                    logger.info("发现插件类: ${clazz.name}")
                }
                allPluginClasses.addAll(pluginClasses)
            }

            // 加载所有发现的插件
            allPluginClasses.forEach { pluginClass ->
                // 检查是否已经加载过同名插件
                val simpleName = pluginClass.simpleName
                if (plugins.containsKey(simpleName)) {
                    logger.info("插件 $simpleName 已存在，跳过加载")
                    return@forEach
                }

                logger.info("发现插件类: ${pluginClass.name}")
                loadSourcePluginClass(pluginClass)
            }

            logger.info("源码插件扫描完成，共发现 ${allPluginClasses.size} 个插件类")
        } catch (e: Exception) {
            logger.warning("扫描源码插件时出错: ${e.message}")
        }
    }

    /**
     * 加载源码插件类
     */
    private fun loadSourcePluginClass(pluginClass: Class<*>) {
        try {
            val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin

            val loadedPlugin = LoadedPlugin(
                plugin = plugin,
                id = pluginClass.name,
                name = "${pluginClass.simpleName}-dev",
                version = "dev",
            )

            val context = contextFactory.createPluginContext(loadedPlugin)

            plugin.activate(context)
            plugins[loadedPlugin.name] = loadedPlugin
            eventBus?.publish(PluginLoaded(loadedPlugin.id, loadedPlugin.name, loadedPlugin.version))

            logger.info("源码插件加载成功: ${loadedPlugin.name} v${loadedPlugin.version} (类: ${pluginClass.name})")
        } catch (e: Exception) {
            logger.warning("加载源码插件类 ${pluginClass.name} 失败: ${e.message}")
        }
    }

    /**
     * 加载JAR格式的插件
     */
    private fun loadJarPlugin(jarFile: File) {
        try {
            logger.info("正在加载插件: ${jarFile.name}")

            // 为插件创建独立类加载器，设置父加载器为应用类加载器
            val loader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this::class.java.classLoader)

            // 读取 Manifest 获取主类与元信息
            val manifest = JarFile(jarFile).use { it.manifest }
            val attrs = manifest?.mainAttributes
            val idFromMf = attrs?.getValue("Plugin-Id")
            val nameFromMf = attrs?.getValue("Plugin-Name")
            val versionFromMf = attrs?.getValue("Plugin-Version")
            val mainClassName = attrs?.getValue("Main-Class")

            // 检查元信息是否缺失
            val missingAttributes = mutableListOf<String>()
            if (idFromMf.isNullOrBlank()) missingAttributes.add("Plugin-Id")
            if (nameFromMf.isNullOrBlank()) missingAttributes.add("Plugin-Name")
            if (versionFromMf.isNullOrBlank()) missingAttributes.add("Plugin-Version")
            if (mainClassName.isNullOrBlank()) missingAttributes.add("Main-Class")

            if (missingAttributes.isNotEmpty()) {
                logger.warning("插件 ${jarFile.name} 缺少必要的元信息: ${missingAttributes.joinToString(", ")}")
                return
            }

            // 加载主类
            val pluginClass = try {
                loader.loadClass(mainClassName)
            } catch (e: ClassNotFoundException) {
                logger.warning("插件 ${jarFile.name} 中未找到主类: $mainClassName")
                return
            }

            val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin

            val loadedPlugin = LoadedPlugin(
                plugin = plugin,
                id = idFromMf!!,
                name = nameFromMf!!,
                version = versionFromMf!!,
                jarFile = jarFile,
                loader = loader
            )

            val context = contextFactory.createPluginContext(loadedPlugin)

            plugin.activate(context)
            plugins[loadedPlugin.name] = loadedPlugin
            pluginLoaders[loadedPlugin.name] = loader
            eventBus?.publish(PluginLoaded(loadedPlugin.id, loadedPlugin.name, loadedPlugin.version))

            logger.info("插件加载成功: ${loadedPlugin.name} v${loadedPlugin.version}")
        } catch (e: Exception) {
            logger.severe("加载插件失败: ${jarFile.name}, 错误: ${e.message}")
            e.printStackTrace()
        }
    }

    fun unloadPlugin(pluginName: String) {
        plugins[pluginName]?.let { pluginInfo ->
            try {
                runCatching { plugins[pluginName]?.plugin?.deactivate() }
                pluginInfo.loader?.close()
                plugins.remove(pluginName)
                pluginLoaders.remove(pluginName)
                eventBus?.publish(PluginUnloaded(pluginInfo.id, pluginName))
                logger.info("插件卸载成功: $pluginName")
            } catch (e: Exception) {
                logger.severe("卸载插件失败: $pluginName, 错误: ${e.message}")
            }
        }
    }
}
