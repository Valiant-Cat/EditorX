package editorx.gui

import com.formdev.flatlaf.FlatLightLaf
import editorx.core.gui.GuiContext
import editorx.core.plugin.PluginManager
import editorx.core.plugin.loader.PluginLoaderImpl
import editorx.core.store.Store
import editorx.core.util.StartupTimer
import editorx.core.util.SystemUtils
import editorx.gui.main.MainWindow
import editorx.gui.plugin.PluginGuiClientImpl
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import javax.swing.SwingUtilities

/**
 * GUI 主入口点
 */
fun main() {
    val startupTimer = StartupTimer("EditorX")

    // 初始化日志系统
    setupLogging()

    // 全局未捕获异常处理器
    Thread.setDefaultUncaughtExceptionHandler { _, ex ->
        LoggerFactory.getLogger("UncaughtException").error("未捕获的异常", ex)
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

private fun setupLogging() {
    runCatching {
        val logFile = File(System.getProperty("user.home"), ".editorx/logs/editorx.log").apply { parentFile.mkdirs() }
        with(System.getProperties()) {
            setProperty("org.slf4j.simpleLogger.logFile", logFile.absolutePath)
            setProperty("org.slf4j.simpleLogger.showDateTime", "true")
            setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS")
            setProperty("org.slf4j.simpleLogger.showThreadName", "true")
            setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
            setProperty("org.slf4j.simpleLogger.showShortLogName", "true")
            setProperty("org.slf4j.simpleLogger.showLogName", "false")
        }
    }.onFailure { e ->
        System.err.println("无法初始化日志目录: ${e.message}")
    }
}

private fun initializeApplication(timer: StartupTimer) {
    // 设置 Look and Feel
    runCatching {
        FlatLightLaf.setup()
    }.onFailure { e ->
        LoggerFactory.getLogger("GuiApp").warn("无法设置 FlatLaf 外观", e)
    }
    timer.mark("laf.setup")

    // macOS 系统菜单集成
    if (SystemUtils.isMacOS()) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "EditorX")
    }

    // 安装全局主题
    ThemeManager.installToSwing()
    timer.mark("theme.installed")

    LoggerFactory.getLogger("GuiApp").info("EditorX 初始化完成")
}

private fun initializeMainWindow(startupTimer: StartupTimer) {
    val appDir = File(System.getProperty("user.home"), ".editorx")
    val guiContext = GuiContext(appDir)
    startupTimer.mark("environment.ready")

    // 恢复保存的语言
    guiContext.settings.get("ui.locale", null)?.let { tag ->
        runCatching { Locale.forLanguageTag(tag) }
            .getOrNull()
            ?.takeIf { it.language.isNotBlank() }
            ?.takeIf { it != editorx.core.i18n.I18n.locale() }
            ?.let { editorx.core.i18n.I18n.setLocale(it) }
    }

    // 恢复保存的主题
    guiContext.settings.get("ui.theme", null)?.let { name ->
        ThemeManager.loadTheme(name).let { theme ->
            ThemeManager.currentTheme = theme
        }
    }

    // 初始化插件化框架
    val disabledPlugins = loadDisabledSet(guiContext.settings)
    val pluginManager = PluginManager().apply {
        setInitialDisabled(disabledPlugins)
        registerContextInitializer { ctx ->
            ctx.setGuiClient(PluginGuiClientImpl(ctx.pluginId(), guiContext))
        }
    }

    SwingUtilities.invokeLater {
        // 扫描并启动需在应用初始化时激活的插件（如语言包）
        pluginManager.scanPlugins(PluginLoaderImpl())
        pluginManager.triggerStartup()
        startupTimer.mark("plugins.started")

        // 创建并显示主窗口
        val mainWindow = MainWindow(guiContext)
        mainWindow.pluginManager = pluginManager
        mainWindow.isVisible = true
        startupTimer.mark("window.visible")

        // 打印启动计时日志
        val startupLogger = LoggerFactory.getLogger("StartupTimer")
        startupTimer.dump(startupLogger)
    }
}

private fun loadDisabledSet(settings: Store): Set<String> {
    return settings.get("plugins.disabled", "")
        ?.splitToSequence(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()
}