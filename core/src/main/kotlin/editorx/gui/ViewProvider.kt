package editorx.gui

import javax.swing.JComponent

/**
 * 视图提供器接口（UI 插件契约）
 */
interface ViewProvider {
    /** 返回要展示的视图组件 */
    fun getView(): JComponent

    /** 返回视图停靠区域 */
    fun area(): ViewArea
}

abstract class CachedViewProvider : ViewProvider {
    private var cachedInstance: JComponent? = null

    override fun getView(): JComponent {
        if (cachedInstance == null) {
            synchronized(this) {
                if (cachedInstance == null) {
                    cachedInstance = createView()
                }
            }
        }
        return cachedInstance!!
    }

    abstract fun createView(): JComponent
}
