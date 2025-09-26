package editorx.plugin

import editorx.command.CommandRegistry
import editorx.event.EventBus
import editorx.gui.ViewProvider
import editorx.settings.SettingsStore
import editorx.syntax.SyntaxAdapter
import editorx.workspace.WorkspaceManager
import java.io.File

/**
 * 插件上下文接口（API）
 *
 * GUI 模块提供该接口的实现，插件通过此上下文与主程序交互。
 */
interface PluginContext {

    /**
     * 在 ActivityBar 注册一个入口按钮，并指定视图提供器
     */
    fun addActivityBarItem(iconPath: String, viewProvider: ViewProvider)

    /**
     * 让主编辑器打开一个文件
     */
    fun openFile(file: File)

    /**
     * 获取全局命令注册表
     */
    fun commands(): CommandRegistry

    /**
     * 获取全局事件总线
     */
    fun eventBus(): EventBus

    /**
     * 获取设置存储
     */
    fun settings(): SettingsStore

    /**
     * 获取工作区管理器
     */
    fun workspace(): WorkspaceManager

    /**
     * 注册语法适配器
     */
    fun registerSyntaxAdapter(syntaxAdapter: SyntaxAdapter)
}
