package editorx.plugin

import editorx.gui.ViewProvider
import editorx.filetype.FileType
import editorx.filetype.SyntaxHighlighter
import editorx.settings.SettingsStore
import editorx.lang.Language
import editorx.workspace.WorkspaceManager

/**
 * 插件上下文接口（API）
 *
 * GUI 模块提供该接口的实现，插件通过此上下文与主程序交互。
 */
interface PluginContext {

    /**
     * 获取设置存储
     */
    fun settings(): SettingsStore

    /**
     * 获取工作区管理器
     */
    fun workspace(): WorkspaceManager

    /**
     * 在 ActivityBar 注册一个入口按钮，并指定视图提供器
     */
    fun addActivityBarItem(iconPath: String, viewProvider: ViewProvider)

    /**
     * 注册文件类型
     */
    fun registerFileType(fileType: FileType)

    /**
     * 注册语法高亮工厂
     */
    fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter)
}
