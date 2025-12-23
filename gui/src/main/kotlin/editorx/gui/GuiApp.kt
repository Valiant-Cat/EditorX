package editorx.gui

import com.formdev.flatlaf.FlatLightLaf
import editorx.gui.core.theme.ThemeManager
import editorx.gui.main.MainWindow
import editorx.core.plugin.PluginManager
import editorx.core.plugin.loader.PluginLoaderImpl
import editorx.gui.plugin.GuiContextImpl
import java.io.File
import org.slf4j.LoggerFactory
import javax.swing.SwingUtilities

/**
 * GUI 主入口点
 */
fun main() {
    // 将日志写入用户目录，便于排查问题（slf4j-simple 需在首次获取 Logger 前设置）
    runCatching {
        val appDir = File(System.getProperty("user.home"), ".editorx")
        val logDir = File(appDir, "logs")
        logDir.mkdirs()
        System.setProperty("org.slf4j.simpleLogger.logFile", File(logDir, "editorx.log").absolutePath)
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS")
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true")
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
    }

    Thread.setDefaultUncaughtExceptionHandler { _, exception ->
        LoggerFactory.getLogger("UncaughtException").error("未捕获的异常", exception)
    }

    SwingUtilities.invokeLater {
        try {
            initializeApplication()
            initializeMainWindow()
        } catch (e: Exception) {
            LoggerFactory.getLogger("GuiApp").error("应用程序启动失败", e)
            System.exit(1)
        }
    }
}

private fun initializeApplication() {
    try {
        FlatLightLaf.setup()
    } catch (e: Exception) {
        LoggerFactory.getLogger("GuiApp").warn("无法设置系统外观", e)
    }

    // 在 macOS 上将菜单集成到系统顶栏；其他平台保留在窗口内
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "EditorX")
    }

    // 安装 Material3 主题到 Swing 默认，确保面板/滚动/标签等遵循统一色板
    ThemeManager.installToSwing()

    LoggerFactory.getLogger("GuiApp").info("EditorX 初始化完成")
}

private fun initializeMainWindow() {
    val appDir = File(System.getProperty("user.home"), ".editorx")
    val environment = GuiEnvironment(appDir)

    val mv = MainWindow(environment)

    // 显示主窗口
    mv.isVisible = true

    // 初始化插件
    val pluginManager = PluginManager()
    pluginManager.registerContextInitializer { pluginContext ->
        val guiContext = GuiContextImpl(mv, pluginContext)
        pluginContext.setGuiContext(guiContext)
    }
    pluginManager.loadAll(PluginLoaderImpl())
    pluginManager.startAll()
    mv.pluginManager = pluginManager
}
