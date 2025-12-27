package editorx.core.plugin

interface PluginContext {

    fun pluginId(): String

    fun pluginInfo(): PluginInfo

    fun gui(): PluginGuiProvider?

    fun active()

    fun deactivate()

    fun isActive(): Boolean

    /**
     * 注册服务（支持多实例）
     * @param serviceClass 服务类型
     * @param instance 服务实例
     */
    fun <T : Any> registerService(serviceClass: Class<T>, instance: T)

    /**
     * 取消注册服务
     * @param serviceClass 服务类型
     * @param instance 服务实例
     */
    fun <T : Any> unregisterService(serviceClass: Class<T>, instance: T)
}
