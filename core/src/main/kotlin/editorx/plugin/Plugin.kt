package editorx.plugin

/**
 * 插件接口
 */
interface Plugin {
    /** 插件被启用 */
    fun activate(context: PluginContext)

    /** 插件被禁用 */
    fun deactivate() {
        // optional method
    }
}
