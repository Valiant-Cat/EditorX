package editorx.plugin

/**
 * 插件上下文工厂接口
 * 负责为每个插件创建独立的PluginContext实例
 */
interface PluginContextFactory {
    
    /**
     * 为指定插件创建独立的PluginContext实例
     * @param loadedPlugin 已加载插件对象
     * @return 插件上下文实例
     */
    fun createPluginContext(loadedPlugin: LoadedPlugin): PluginContext
}
