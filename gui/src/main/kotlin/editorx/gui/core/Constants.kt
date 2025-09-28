package editorx.gui.core

object Constants {

    /** ActivityBar 默认选中的 ID */
    const val ACTIVITY_BAR_DEFAULT_ID = "explorer"

    /** ActivityBar 默认的排序规则 */
    val ACTIVITY_BAR_ORDER = listOf(
        "explorer",    // 文件浏览器
    )

    /** 获取插件在 ActivityBar 的排序权重 */
    fun getPluginOrderInActivityBar(pluginId: String): Int {
        return ACTIVITY_BAR_ORDER.indexOf(pluginId).let { index ->
            if (index >= 0) index else Int.MAX_VALUE
        }
    }
}