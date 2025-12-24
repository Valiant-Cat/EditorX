package editorx.gui

import com.formdev.flatlaf.FlatLightLaf
import editorx.core.plugin.PluginManager
import editorx.core.plugin.loader.PluginLoaderImpl
import editorx.core.settings.SettingsStore
import editorx.core.util.StartupTimer
import editorx.gui.core.theme.ThemeManager
import editorx.gui.main.MainWindow
import editorx.gui.plugin.GuiContextImpl
import java.io.File
import java.util.Locale
import javax.swing.SwingUtilities
import org.slf4j.LoggerFactory

/**
 * GUI 主入口点
 */
fun main() {
    val startupTimer = StartupTimer("EditorX")
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
            initializeApplication(startupTimer)
            initializeMainWindow(startupTimer)
        } catch (e: Exception) {
            LoggerFactory.getLogger("GuiApp").error("应用程序启动失败", e)
            System.exit(1)
        }
    }
}

private fun initializeApplication(timer: StartupTimer) {
    try {
        FlatLightLaf.setup()
    } catch (e: Exception) {
        LoggerFactory.getLogger("GuiApp").warn("无法设置系统外观", e)
    }
    timer.mark("laf.setup")

    // 在 macOS 上将菜单集成到系统顶栏；其他平台保留在窗口内
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "EditorX")
    }

    // 安装 Material3 主题到 Swing 默认，确保面板/滚动/标签等遵循统一色板
    ThemeManager.installToSwing()
    timer.mark("theme.installed")

    LoggerFactory.getLogger("GuiApp").info("EditorX 初始化完成")
}

private fun initializeMainWindow(startupTimer: StartupTimer) {
    val appDir = File(System.getProperty("user.home"), ".editorx")
    val environment = GuiEnvironment(appDir)
    val disabled = loadDisabledSet(environment.settings)
    startupTimer.mark("environment.ready")

    environment.settings.get("ui.locale", null)?.let { tag ->
        runCatching { Locale.forLanguageTag(tag) }
            .getOrNull()
            ?.takeIf { it.language.isNotBlank() }
            ?.let { locale ->
                if (editorx.core.i18n.I18n.locale() != locale) {
                    editorx.core.i18n.I18n.setLocale(locale)
                }
            }
    }

    val mv = MainWindow(environment)

    // 显示主窗口
    mv.isVisible = true
    startupTimer.mark("window.visible")

    // 初始化插件
    val pluginManager = PluginManager()
    pluginManager.setInitialDisabled(disabled)
    pluginManager.registerContextInitializer { pluginContext ->
        val guiContext = GuiContextImpl(mv, pluginContext)
        pluginContext.setGuiContext(guiContext)
    }
    mv.pluginManager = pluginManager
    val loadLogger = LoggerFactory.getLogger("StartupTimer")
    Thread {
        val loadTimer = StartupTimer("PluginLoad")
        pluginManager.loadAll(PluginLoaderImpl())
        loadTimer.mark("scanned")
        SwingUtilities.invokeLater {
            startupTimer.mark("plugins.loaded")
            pluginManager.triggerStartup()
            startupTimer.mark("plugins.started")
            loadTimer.mark("started")
            loadTimer.dump(loadLogger)
            startupTimer.dump(loadLogger)
        }
    }.apply { name = "plugin-loader"; isDaemon = true }.start()
}

private fun loadDisabledSet(settings: SettingsStore): Set<String> {
    return settings.get("plugins.disabled", "")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()
}
