package editorx.gui.plugin

import editorx.command.CommandRegistry
import editorx.event.EventBus
import editorx.gui.ViewProvider
import editorx.gui.main.MainWindow
import editorx.plugin.LoadedPlugin
import editorx.plugin.PluginContext
import editorx.settings.SettingsStore
import editorx.syntax.SyntaxHighlighterManager
import editorx.syntax.SyntaxHighlighterProvider
import editorx.workspace.WorkspaceManager
import java.io.File
import java.util.logging.Logger

/**
 * GUI 实现的插件上下文
 * 为每个插件创建独立的实例，包含插件标识信息
 */
class GuiPluginContext(
    private val mainWindow: MainWindow,
    private val loadedPlugin: LoadedPlugin,
) : PluginContext {

    private val logger =
        Logger.getLogger("${GuiPluginContext::class.java.name}[${loadedPlugin.name}-${loadedPlugin.version}]")

    override fun addActivityBarItem(iconPath: String, viewProvider: ViewProvider) {
        mainWindow.activityBar.addItem(loadedPlugin.id, loadedPlugin.name, iconPath, viewProvider)
    }

    override fun openFile(file: File) {
        try {
            mainWindow.editor.openFile(file)
        } catch (e: Exception) {
            logger.warning("打开文件失败: ${file.name}, 错误: ${e.message}")
        }
    }

    override fun commands(): CommandRegistry = mainWindow.services.commands
    override fun eventBus(): EventBus = mainWindow.services.eventBus
    override fun settings(): SettingsStore = mainWindow.services.settings
    override fun workspace(): WorkspaceManager = mainWindow.services.workspace

    override fun registerSyntaxHighlighter(provider: SyntaxHighlighterProvider) {
        SyntaxHighlighterManager.registerHighlighterProvider(provider)
    }
}
