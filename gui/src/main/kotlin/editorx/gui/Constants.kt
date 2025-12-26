package editorx.gui

object Constants {

    /** ActivityBar 默认选中的 ID */
    const val ACTIVITY_BAR_DEFAULT_ID = "explorer"

    /** ActivityBar 默认的排序规则 */
    val ACTIVITY_BAR_ORDER = listOf(
        "explorer",    // 文件浏览器
        "search",      // 全局搜索
        "git",         // Git 版本控制
    )

}
