package editorx.gui

import com.formdev.flatlaf.FlatLightLaf
import editorx.command.CommandMeta
import editorx.gui.plugin.GuiPluginContext
import editorx.gui.theme.ThemeManager
import editorx.gui.services.GuiServices
import editorx.gui.ui.MainWindow
import editorx.gui.ui.command.CommandPalette
import editorx.plugin.LoadedPlugin
import editorx.plugin.PluginContext
import editorx.plugin.PluginContextFactory
import editorx.plugin.PluginManager
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Editor GUI 主入口点
 */
fun main() {
    Logger.getLogger("").level = Level.INFO

    Thread.setDefaultUncaughtExceptionHandler { _, exception ->
        Logger.getLogger("").severe("未捕获的异常: ${exception.message}")
        exception.printStackTrace()
    }

    SwingUtilities.invokeLater {
        try {
            initializeApplication()
            initializeMainWindow()
        } catch (e: Exception) {
            Logger.getLogger("").severe("应用程序启动失败: ${e.message}")
            e.printStackTrace()
            System.exit(1)
        }
    }
}

private fun initializeApplication() {
    try {
        FlatLightLaf.setup()
    } catch (e: Exception) {
        Logger.getLogger("").warning("无法设置系统外观: ${e.message}")
    }

    // 使用 FlatLaf 时在 macOS 上避免启用原生屏幕菜单栏，以防 Aqua 与 FlatLaf 冲突
    System.setProperty("apple.laf.useScreenMenuBar", "false")
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "EditorX")

    // 安装 Material3 主题到 Swing 默认，确保面板/滚动/标签等遵循统一色板
    ThemeManager.installToUI()

    Logger.getLogger("").info("EditorX 初始化完成")
}

private fun initializeMainWindow() {
    val appDir = File(System.getProperty("user.home"), ".editorx")
    val services = GuiServices(appDir)

    val mv = MainWindow(services)

    // 显示主窗口
    mv.isVisible = true

    // 初始化插件
    val contextFactory = object : PluginContextFactory {
        override fun createPluginContext(loadedPlugin: LoadedPlugin): PluginContext {
            return GuiPluginContext(mv, loadedPlugin)
        }
    }
    val pluginManager = PluginManager(contextFactory, services.eventBus)
    mv.pluginManager = pluginManager
    pluginManager.loadPlugins()

    // Bind builtin command handlers that need GUI components
    val commands = services.commands
    commands.register(CommandMeta("help.commandPalette", "打开命令面板")) {
        CommandPalette.show(mv, services.commands)
    }
    commands.register(CommandMeta("app.about", "关于 EditorX")) {
        javax.swing.JOptionPane.showMessageDialog(mv, "EditorX – 可扩展插件化编辑器", "关于", javax.swing.JOptionPane.INFORMATION_MESSAGE)
    }
    commands.register(CommandMeta("view.toggleSidebar", "切换侧边栏")) { mv.sideBar.isVisible = !mv.sideBar.isVisible }
    commands.register(CommandMeta("view.togglePanel", "切换底部面板")) { mv.panel.isVisible = !mv.panel.isVisible }
    commands.register(CommandMeta("file.open", "打开文件")) { mv.openFileChooserAndOpen() }
    commands.register(CommandMeta("file.save", "保存文件")) { mv.editor.saveCurrent() }
    commands.register(CommandMeta("file.saveAs", "另存为...")) { mv.editor.saveCurrentAs() }
}
