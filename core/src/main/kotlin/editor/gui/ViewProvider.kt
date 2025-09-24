package editor.gui

import javax.swing.JComponent

/**
 * 视图提供器接口（UI 插件契约）
 */
interface ViewProvider {
    /** 返回要展示的视图组件 */
    fun createView(): JComponent

    /** 返回视图停靠区域 */
    fun area(): ViewArea
}
