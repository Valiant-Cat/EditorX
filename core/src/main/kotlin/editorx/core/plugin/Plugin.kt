package editorx.core.plugin

/**
 * 插件接口
 */
interface Plugin {
    /** 获取插件信息 */
    fun getInfo(): PluginInfo

    /** 插件被启用 */
    fun activate(pluginContext: PluginContext)

    /** 插件被禁用 */
    fun deactivate() {
        // optional method
    }

    /**
     * 声明插件激活事件；默认随启动加载。
     */
    fun activationEvents(): List<ActivationEvent> = listOf(ActivationEvent.OnStartup)

    /**
     * 声明重启策略；默认支持热启停。
     */
    fun restartPolicy(): PluginRestartPolicy = PluginRestartPolicy.DYNAMIC
}
