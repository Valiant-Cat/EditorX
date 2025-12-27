package editorx.core.plugin.loader

import editorx.core.plugin.LoadedPlugin

/**
 * 双重插件加载器
 * 组合 SourcePluginLoader 和 JarPluginLoader，同时加载源码插件和 JAR 插件
 */
class DuplexPluginLoader : PluginLoader {

    private val sourceLoader = SourcePluginLoader()
    private val jarLoader = JarPluginLoader()

    override fun load(): List<LoadedPlugin> {
        val allPlugins = mutableListOf<LoadedPlugin>()
        allPlugins.addAll(sourceLoader.load())
        allPlugins.addAll(jarLoader.load())
        return allPlugins.sortedBy { it.plugin.javaClass.simpleName }
    }
}

