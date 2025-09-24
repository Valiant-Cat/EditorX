package editorx.gui

import editorx.gui.plugin.GuiPluginInitializer
import editorx.gui.ui.MainWindow
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
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Exception) {
        Logger.getLogger("").warning("无法设置系统外观: ${e.message}")
    }

    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "EditorX")

    Logger.getLogger("").info("EditorX 初始化完成")
}

private fun initializeMainWindow() {
    // 显示主窗口
    val mv = MainWindow()
    mv.isVisible = true

    // 初始化插件
    GuiPluginInitializer.initialize(mv)
}
