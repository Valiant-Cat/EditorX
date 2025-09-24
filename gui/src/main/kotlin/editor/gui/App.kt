package editor.gui

import javax.swing.SwingUtilities
import javax.swing.UIManager
import java.util.logging.Logger
import java.util.logging.Level

/**
 * APK Editor 主入口点
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
            startMainWindow()
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
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "APK Editor")

    Logger.getLogger("").info("APK Editor 初始化完成")
}

private fun startMainWindow() {
    val mainFrame = MainFrame.instance
    mainFrame.isVisible = true

    Logger.getLogger("").info("APK Editor 主窗口已启动")
}

