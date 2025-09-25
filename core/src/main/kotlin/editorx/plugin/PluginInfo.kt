package editorx.plugin

/**
 * 插件信息数据类
 * 包含插件的基本元信息
 */
data class PluginInfo(
    /** 插件唯一标识符 */
    val id: String,
    /** 插件显示名称 */
    val name: String,
    /** 插件版本 */
    val version: String,
)
