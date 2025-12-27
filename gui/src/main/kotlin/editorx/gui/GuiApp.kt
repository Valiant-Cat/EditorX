package editorx.gui

import com.formdev.flatlaf.FlatLightLaf
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.PluginManager
import editorx.core.plugin.PluginState
import editorx.core.plugin.PluginLoaderImpl
import editorx.core.util.FileStore
import editorx.core.util.Store
import editorx.core.util.StartupTimer
import editorx.core.util.SystemUtils
import editorx.core.workspace.DefaultWorkspace
import editorx.gui.core.ShortcutIds
import editorx.gui.core.ShortcutRegistry
import editorx.gui.main.MainWindow
import editorx.gui.search.SearchDialog
import editorx.gui.settings.SettingsDialog
import editorx.gui.theme.ThemeManager
import org.slf4j.LoggerFactory
import java.awt.Image
import java.awt.Taskbar
import java.awt.event.KeyEvent
import java.io.File
import java.io.IOException
import java.util.*
import javax.imageio.ImageIO
import javax.swing.KeyStroke
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
    // 设置应用图标
    setAppIcon("icon_round_128.png")
    timer.mark("icon.set")

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


private fun setAppIcon(icon: String) {
    val classLoader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    val iconUrl = classLoader.getResource(icon) ?: run {
        return
    }

    val image = try {
        ImageIO.read(iconUrl)
    } catch (e: IOException) {
        return
    }

    if (image == null) {
        return
    }

    // 优先尝试现代 Taskbar API（Windows / 新版本的 macOS）
    if (Taskbar.isTaskbarSupported()) {
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            taskbar.iconImage = image
            return
        }
    }

    // macOS 专属回退（目前仍是主要方式）
    if (SystemUtils.isMacOS()) {
        runCatching {
            val appClass = Class.forName("com.apple.eawt.Application")
            val application = appClass.getMethod("getApplication").invoke(null)
            appClass.getMethod("setDockIconImage", Image::class.java).invoke(application, image)
        }
    }
}

private fun initializeMainWindow(startupTimer: StartupTimer) {
    val appDir = File(System.getProperty("user.home"), ".editorx")

    // 创建 settings 和 workspace
    val settings = FileStore(File(appDir, "settings.properties"))
    val workspace = DefaultWorkspace(settings)

    // 恢复保存的语言
    settings.get("ui.locale", null)?.let { tag ->
        runCatching { Locale.forLanguageTag(tag) }
            .getOrNull()
            ?.takeIf { it.language.isNotBlank() }
            ?.takeIf { it != editorx.core.i18n.I18n.locale() }
            ?.let { editorx.core.i18n.I18n.setLocale(it) }
    }

    // 恢复保存的主题
    settings.get("ui.theme", null)?.let { name ->
        ThemeManager.loadTheme(name).let { theme ->
            ThemeManager.currentTheme = theme
        }
    }

    // 初始化插件框架
    val disabledPlugins = loadDisabledSet(settings)
    val pluginManager = PluginManager().apply {
        setInitialDisabled(disabledPlugins)
        scanPlugins(PluginLoaderImpl())
    }

    SwingUtilities.invokeLater {
        // 创建 GUI 上下文 与 主窗口
        val guiContext = GuiContextImpl(appDir, settings, workspace, pluginManager)
        val mainWindow = MainWindow(guiContext).apply {
            pluginManager.setGuiProviderFactory { pluginCtx ->
                PluginGuiProviderImpl(pluginCtx.pluginId(), guiContext, this)
            }
            pluginManager.addPluginStateListener(object : PluginManager.PluginStateListener {
                override fun onPluginStateChanged(pluginId: String) {
                    val state = guiContext.getPluginManager().getPlugin(pluginId)?.state
                    if (state != PluginState.STARTED) {
                        // 插件被停用/失败：移除可能残留的入口与视图
                        sideBar.removeView(pluginId)
                        // 移除 ToolBar items
                        toolBar.removeItems(pluginId)
                    }
                    editor.refreshSyntaxForOpenTabs()
                }
            })
        }

        // 显示主窗口
        mainWindow.isVisible = true
        setupShortcuts(mainWindow)
        startupTimer.mark("gui.ready")

        // 触发插件启动
        pluginManager.triggerStartup()
        startupTimer.mark("plugin.triggerStartup")

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


/**
 * 统一设置所有快捷键
 */
private fun setupShortcuts(mainWindow: MainWindow) {
    // 双击 Shift - 全局搜索
    ShortcutRegistry.registerDoubleShortcut(
        id = ShortcutIds.Global.SEARCH,
        keyCode = KeyEvent.VK_SHIFT,
        nameKey = I18nKeys.Action.GLOBAL_SEARCH,
        descriptionKey = I18nKeys.Shortcut.GLOBAL_SEARCH,
        action = { showSearchDialog(mainWindow) }
    )

    // Command+, - 打开设置
    val shortcutMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    ShortcutRegistry.registerShortcut(
        id = ShortcutIds.Global.SETTINGS,
        keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, shortcutMask),
        nameKey = I18nKeys.Toolbar.SETTINGS,
        descriptionKey = I18nKeys.Shortcut.OPEN_SETTINGS,
        action = { showSettings(mainWindow) }
    )

    // Command+N - 新建文件
    ShortcutRegistry.registerShortcut(
        id = ShortcutIds.Editor.NEW_FILE,
        keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask),
        nameKey = I18nKeys.Action.NEW_FILE,
        descriptionKey = I18nKeys.Action.NEW_FILE,
        action = { mainWindow.editor.newUntitledFile() }
    )

    // Command+W - 关闭当前标签页
    ShortcutRegistry.registerShortcut(
        id = ShortcutIds.Editor.CLOSE_TAB,
        keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutMask),
        nameKey = I18nKeys.Action.CLOSE,
        descriptionKey = I18nKeys.Shortcut.CLOSE_TAB
    ) {
        // 检查焦点是否在 editor 上
        val focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, mainWindow.editor)) {
            mainWindow.editor.closeCurrentTab()
        }
    }

    // Option+Command+L (macOS) 或 Alt+Ctrl+L (其他系统) - 格式化文件
    val formatShortcutMask = if (System.getProperty("os.name").lowercase().contains("mac")) {
        java.awt.event.InputEvent.ALT_DOWN_MASK or java.awt.event.InputEvent.META_DOWN_MASK
    } else {
        java.awt.event.InputEvent.ALT_DOWN_MASK or java.awt.event.InputEvent.CTRL_DOWN_MASK
    }
    ShortcutRegistry.registerShortcut(
        id = ShortcutIds.Editor.FORMAT_FILE,
        keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_L, formatShortcutMask),
        nameKey = I18nKeys.Action.FORMAT_FILE,
        descriptionKey = I18nKeys.Shortcut.FORMAT_FILE
    ) {
        // 检查焦点是否在 editor 上
        val focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, mainWindow.editor)) {
            mainWindow.editor.formatCurrentFile()
        }
    }
}

/**
 * 显示全局搜索对话框
 */
private fun showSearchDialog(mainWindow: MainWindow) {
    val dialog = SearchDialog(mainWindow, mainWindow)
    dialog.showDialog()
}

/**
 * 显示设置对话框
 */
private fun showSettings(mainWindow: MainWindow) {
    SettingsDialog.showOrBringToFront(
        mainWindow,
        mainWindow.guiContext,
        mainWindow.guiContext.getPluginManager(),
        SettingsDialog.Section.APPEARANCE
    )
}