package editorx.plugin

import java.io.File
import java.net.URLClassLoader
import java.util.logging.Logger

/**
 * JAR插件加载器
 */
class JarPluginScanner(
    private val contextFactory: PluginContextFactory,
) : PluginScanner {

    private val logger = Logger.getLogger(JarPluginScanner::class.java.name)

    override fun scanPlugins(): List<LoadedPlugin> {
        val loadedPlugins = mutableListOf<LoadedPlugin>()

        try {
            val pluginsJars = scanPluginJars()
            logger.info("找到 ${pluginsJars.size} 个JAR插件文件")

            pluginsJars.forEach { jarFile ->
                try {
                    logger.info("正在加载JAR插件: ${jarFile.name}")

                    // 为插件创建独立类加载器，设置父加载器为应用类加载器
                    val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), this::class.java.classLoader)

                    // 直接扫描JAR中的Plugin实现类
                    val pluginMainClass = findPluginMainClassInJar(classLoader, jarFile)
                    if (pluginMainClass == null) {
                        logger.warning("插件 ${jarFile.name} 中未找到实现Plugin接口的类")
                        return@forEach
                    }
                    logger.info("找到插件主类: ${pluginMainClass.name}")

                    val plugin = pluginMainClass.getDeclaredConstructor().newInstance() as Plugin

                    val loadedPlugin = LoadedPlugin(
                        plugin = plugin,
                        classLoader = classLoader
                    )

                    val context = contextFactory.createPluginContext(loadedPlugin)

                    plugin.activate(context)
                    loadedPlugins.add(loadedPlugin)

                    logger.info("JAR插件加载成功: ${loadedPlugin.name} v${loadedPlugin.version}")
                } catch (e: Exception) {
                    logger.severe("加载JAR插件失败: ${jarFile.name}, 错误: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            logger.warning("扫描JAR插件时出错: ${e.message}")
        }

        return loadedPlugins
    }

    /**
     * 扫描插件JAR文件
     */
    private fun scanPluginJars(): Array<File> {
        val pluginDir = File("plugins")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
            logger.info("插件目录不存在，已创建: ${pluginDir.absolutePath}")
        } else {
            return pluginDir.listFiles { file -> file.isFile && file.extension.lowercase() == "jar" } ?: arrayOf()
        }
        return arrayOf()
    }

    /**
     * 在JAR文件中查找实现Plugin接口的类
     */
    private fun findPluginMainClassInJar(classLoader: URLClassLoader, jarFile: File): Class<*>? {
        val pluginClasses = mutableListOf<Class<*>>()

        try {
            val jarFileObj = java.util.jar.JarFile(jarFile)
            val entries = jarFileObj.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".class") && !entry.name.contains("$")) {
                    val className = entry.name.replace('/', '.').replace(".class", "")

                    try {
                        val clazz = classLoader.loadClass(className)
                        if (Plugin::class.java.isAssignableFrom(clazz) && !clazz.isInterface) {
                            pluginClasses.add(clazz)
                            logger.info("找到Plugin实现类: $className")
                        }
                    } catch (e: Exception) {
                        // 忽略无法加载的类
                        logger.fine("无法加载类 $className: ${e.message}")
                    }
                }
            }
            jarFileObj.close()
        } catch (e: Exception) {
            logger.warning("扫描JAR文件 ${jarFile.name} 时出错: ${e.message}")
        }

        return pluginClasses.firstOrNull()
    }
}
