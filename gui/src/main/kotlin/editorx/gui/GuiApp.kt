package editorx.gui

import com.formdev.flatlaf.FlatLightLaf
import editorx.gui.core.theme.ThemeManager
import editorx.gui.main.MainWindow
import editorx.core.plugin.PluginManager
import editorx.core.plugin.loader.PluginLoaderImpl
import editorx.gui.plugin.GuiContextImpl
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingUtilities

/**
 * GUI 主入口点
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

    // 在 macOS 上将菜单集成到系统顶栏；其他平台保留在窗口内
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "EditorX")
    }

    // 安装 Material3 主题到 Swing 默认，确保面板/滚动/标签等遵循统一色板
    ThemeManager.installToSwing()

    Logger.getLogger("").info("EditorX 初始化完成")
}

private fun initializeMainWindow() {
    val appDir = File(System.getProperty("user.home"), ".editorx")
    val environment = GuiEnvironment(appDir)

    val mv = MainWindow(environment)

    // 显示主窗口
    mv.isVisible = true

    // 初始化插件
    val pluginManager = PluginManager()
    pluginManager.registerAddPluginListener { pluginContext ->
        val guiContext = GuiContextImpl(mv, pluginContext)
        pluginContext.setGuiContext(guiContext)
    }
    pluginManager.loadPlugins(PluginLoaderImpl())
    pluginManager.activeAllPlugins()
    mv.pluginManager = pluginManager
}
