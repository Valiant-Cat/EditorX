package editor.plugin

import editor.gui.ViewProvider
import java.io.File

/**
 * 插件上下文接口（API）
 *
 * GUI 模块提供该接口的实现，插件通过此上下文与主程序交互。
 */
interface PluginContext {
    /**
     * 在 ActivityBar 注册一个入口按钮，并指定其视图提供器
     */
    fun addActivityBarItem(id: String, iconPath: String, tooltip: String, viewProvider: ViewProvider)

    /**
     * 让主编辑器打开一个文件
     */
    fun openFile(file: File)
}

