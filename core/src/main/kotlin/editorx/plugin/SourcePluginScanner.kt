package editorx.plugin

import java.util.logging.Logger

/**
 * 源码插件加载器
 */
class SourcePluginScanner(
    private val contextFactory: PluginContextFactory,
) : PluginScanner {

    private val logger = Logger.getLogger(SourcePluginScanner::class.java.name)

    override fun scanPlugins(): List<LoadedPlugin> {
        val loadedPlugins = mutableListOf<LoadedPlugin>()

        try {
            val pluginClasses = scanSourcePlugins()
            logger.info("找到 ${pluginClasses.size} 个源码插件类")

            pluginClasses.forEach { pluginClass ->
                try {
                    val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin
                    val loadedPlugin = LoadedPlugin(
                        plugin = plugin,
                        classLoader = null // 源码插件使用当前类加载器
                    )

                    val context = contextFactory.createPluginContext(loadedPlugin)
                    plugin.activate(context)

                    loadedPlugins.add(loadedPlugin)

                    logger.info("源码插件加载成功: ${loadedPlugin.name} v${loadedPlugin.version}")
                } catch (e: Exception) {
                    logger.severe("加载源码插件失败: ${pluginClass.simpleName}, 错误: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            logger.warning("扫描源码插件时出错: ${e.message}")
        }

        return loadedPlugins
    }

    /**
     * 扫描源码插件
     */
    private fun scanSourcePlugins(): List<Class<*>> {
        val pluginClasses = mutableListOf<Class<*>>()

        try {
            // 扫描 editorx.plugins 包下的所有类
            val packageName = "editorx.plugins"
            val classLoader = this::class.java.classLoader

            // 使用反射扫描包
            val packagePath = packageName.replace('.', '/')
            val resources = classLoader.getResources(packagePath)

            while (resources.hasMoreElements()) {
                val resource = resources.nextElement()
                if (resource.protocol == "file") {
                    val directory = java.io.File(resource.path)
                    if (directory.exists() && directory.isDirectory) {
                        scanDirectoryForPlugins(directory, packageName, pluginClasses)
                    }
                }
            }

            logger.info("源码插件扫描完成，找到 ${pluginClasses.size} 个插件类")
        } catch (e: Exception) {
            logger.warning("扫描源码插件时出错: ${e.message}")
        }

        return pluginClasses
    }

    /**
     * 递归扫描目录中的插件类
     */
    private fun scanDirectoryForPlugins(
        directory: java.io.File,
        packageName: String,
        pluginClasses: MutableList<Class<*>>
    ) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                // 递归扫描子目录
                val subPackageName = if (packageName.isEmpty()) file.name else "$packageName.${file.name}"
                scanDirectoryForPlugins(file, subPackageName, pluginClasses)
            } else if (file.name.endsWith(".class")) {
                // 检查是否是插件类
                val className = if (packageName.isEmpty()) file.nameWithoutExtension
                else "$packageName.${file.nameWithoutExtension}"

                try {
                    val clazz = Class.forName(className)
                    if (Plugin::class.java.isAssignableFrom(clazz) && !clazz.isInterface && !java.lang.reflect.Modifier.isAbstract(
                            clazz.modifiers
                        )
                    ) {
                        pluginClasses.add(clazz)
                        logger.info("找到源码插件类: $className")
                    }
                } catch (e: Exception) {
                    // 忽略无法加载的类
                    logger.fine("无法加载类 $className: ${e.message}")
                }
            }
        }
    }
}
