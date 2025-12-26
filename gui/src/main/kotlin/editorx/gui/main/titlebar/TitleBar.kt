package editorx.gui.main.titlebar

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.external.ApkTool
import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import editorx.core.util.SystemUtils
import editorx.gui.ThemeManager
import editorx.gui.main.MainWindow
import editorx.gui.main.explorer.Explorer
import editorx.gui.search.SearchDialog
import editorx.gui.settings.SettingsDialog
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.ActionListener
import java.io.File
import java.util.*
import javax.swing.*

/**
 * 顶部工具栏：常用操作的快捷入口。
 * 在 macOS 上菜单集成到系统顶栏时，窗口内仍保留此工具栏以便操作。
 */
class TitleBar(private val mainWindow: MainWindow) : JToolBar() {
    companion object {
        private val logger = LoggerFactory.getLogger(TitleBar::class.java)
        private const val ICON_SIZE = 18
    }

    private var toggleSideBarButton: JButton? = null
    private var buildButton: JButton? = null
    private var searchButton: JButton? = null
    private var settingsButton: JButton? = null
    private var compileTask: Thread? = null
    private var titleLabel: JLabel? = null

    // VCS Widget
    val vcsWidget = VcsWidget(mainWindow.guiContext.getWorkspace())

    init {
        isFloatable = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = true
        updateTheme()
        buildActions()

        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }

        // 添加双击空白处全屏功能
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && e.button == java.awt.event.MouseEvent.BUTTON1) {
                    // 检查是否点击在空白区域（不是按钮上）
                    val component = SwingUtilities.getDeepestComponentAt(
                        mainWindow,
                        e.x,
                        e.y
                    )
                    // 只有在点击工具栏本身（空白处）时才触发全屏
                    if (component == this@TitleBar || component is Box.Filler) {
                        toggleFullScreen()
                    }
                }
            }
        })
    }

    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        background = theme.toolbarBackground
        titleLabel?.foreground = theme.onSurface
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, theme.statusBarSeparator),
            BorderFactory.createEmptyBorder(2, 5, 2, 5),
        )
        
        // 更新所有按钮的图标
        buildButton?.icon = IconLoader.getIcon(
            IconRef("icons/common/build.svg"),
            ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { theme.onSurface }
        )
        searchButton?.icon = IconLoader.getIcon(
            IconRef("icons/common/search.svg"),
            ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { theme.onSurface }
        )
        settingsButton?.icon = IconLoader.getIcon(
            IconRef("icons/common/settings.svg"),
            ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { theme.onSurface }
        )
        toggleSideBarButton?.icon = getSideBarIcon()
        
        revalidate()
        repaint()
    }


    /**
     * 更新 VCS Widget 的显示内容
     */
    fun updateVcsDisplay() {
        vcsWidget.updateDisplay()
    }

    /**
     * 兼容性方法：保持向后兼容
     */
    @Deprecated("使用 updateVcsDisplay() 代替", ReplaceWith("updateVcsDisplay()"))
    fun updateProjectDisplay() {
        updateVcsDisplay()
    }


    private fun JButton.compact(textLabel: String, l: ActionListener): JButton = apply {
        toolTipText = textLabel
        isFocusable = false
        margin = Insets(4, 4, 4, 4)
        addActionListener(l)
    }

    /**
     * 创建带快捷键提示的紧凑按钮
     */
    private fun JButton.compactWithShortcut(textLabel: String, shortcut: String, l: ActionListener): JButton = apply {
        toolTipText = "$textLabel ($shortcut)"
        isFocusable = false
        margin = Insets(4, 4, 4, 4)
        addActionListener(l)
    }

    private fun buildActions() {
        setupLeftActions()
        add(Box.createHorizontalGlue())
        setupCenterTitle()
        add(Box.createHorizontalGlue())
        setupRightActions()
    }

    private fun setupLeftActions() {
        if (SystemUtils.isMacOS()) {
            // macOS 模式：左侧留空（给系统控制按钮），中间显示标题，右侧显示按钮
            add(Box.createHorizontalStrut(70)) // 为 macOS 交通灯按钮留空间
        }

        // VCS Widget（最左侧）
        add(vcsWidget)
        add(Box.createHorizontalStrut(12))
    }

    private fun setupCenterTitle() {
        titleLabel = JLabel("EditorX").apply {
            font = font.deriveFont(Font.BOLD, 13f)
            foreground = ThemeManager.currentTheme.onSurface
            isOpaque = false
        }
        add(titleLabel)
    }

    private fun setupRightActions() {
        buildButton = JButton(
            IconLoader.getIcon(
                IconRef("icons/common/build.svg"),
                ICON_SIZE,
                adaptToTheme = true,
                getThemeColor = { ThemeManager.currentTheme.onSurface }
            )
        ).compact(I18n.translate(I18nKeys.Toolbar.BUILD)) {
            compileWorkspaceApk()
        }
        add(buildButton)

        add(Box.createHorizontalStrut(12))
        addSeparator()
        add(Box.createHorizontalStrut(12))

        toggleSideBarButton = JButton(getSideBarIcon()).compact(I18n.translate(I18nKeys.Toolbar.TOGGLE_SIDEBAR)) {
            toggleSideBar()
        }
        add(toggleSideBarButton)

        add(Box.createHorizontalStrut(6))

        // 全局搜索按钮（双击 Shift）
        val doubleShiftText = I18n.translate(I18nKeys.Toolbar.DOUBLE_SHIFT)
        searchButton = JButton(
            IconLoader.getIcon(
                IconRef("icons/common/search.svg"),
                ICON_SIZE,
                adaptToTheme = true,
                getThemeColor = { ThemeManager.currentTheme.onSurface }
            )
        ).compactWithShortcut(I18n.translate(I18nKeys.Toolbar.GLOBAL_SEARCH), doubleShiftText) {
            showGlobalSearch()
        }
        add(searchButton)

        add(Box.createHorizontalStrut(6))

        settingsButton = JButton(
            IconLoader.getIcon(
                IconRef("icons/common/settings.svg"),
                ICON_SIZE,
                adaptToTheme = true,
                getThemeColor = { ThemeManager.currentTheme.onSurface }
            )
        ).compact(I18n.translate(I18nKeys.Toolbar.SETTINGS)) {
            showSettings()
        }
        add(settingsButton)

        add(Box.createHorizontalStrut(12))
    }

    private fun openFolder() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = I18n.translate(I18nKeys.Dialog.SELECT_FOLDER)
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val selected = chooser.selectedFile
            mainWindow.guiContext.getWorkspace().openWorkspace(selected)
            mainWindow.guiContext.getWorkspace().addRecentWorkspace(selected)
            (mainWindow.sideBar.getView("explorer") as? Explorer)?.refreshRoot()
            mainWindow.titleBar.updateVcsDisplay()
            mainWindow.editor.showEditorContent()
        }
    }

    fun updateSideBarIcon(sideBarVisible: Boolean) {
        toggleSideBarButton?.icon = getSideBarIcon()
    }

    private fun getSideBarIcon(): Icon? {
        val isVisible = mainWindow.sideBar.isSideBarVisible()
        val iconName = if (isVisible) "icons/gui/layout-sidebar-left.svg" else "icons/gui/layout-sidebar-left-off.svg"
        return IconLoader.getIcon(
            IconRef(iconName), 
            ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { ThemeManager.currentTheme.onSurface }
        )
    }

    private fun toggleSideBar() {
        val sidebar = mainWindow.sideBar
        if (sidebar.isSideBarVisible()) sidebar.hideSideBar() else sidebar.getCurrentViewId()
            ?.let { sidebar.showView(it) }
        toggleSideBarButton?.icon = getSideBarIcon()
    }

    private fun showFindDialog() {
        mainWindow.editor.showFindBar()
    }

    private fun showReplaceDialog() {
        mainWindow.editor.showReplaceBar()
    }

    private fun showSettings() {
        val pm = mainWindow.guiContext.getPluginManager()
        SettingsDialog.showOrBringToFront(mainWindow, mainWindow.guiContext, pm, SettingsDialog.Section.APPEARANCE)
    }

    private fun showGlobalSearch() {
        val dialog = SearchDialog(SwingUtilities.getWindowAncestor(this), mainWindow)
        dialog.showDialog()
    }

    private fun toggleFullScreen() {
        val frame = mainWindow
        if (frame.extendedState and JFrame.MAXIMIZED_BOTH == JFrame.MAXIMIZED_BOTH) {
            // 当前是全屏状态，退出全屏
            frame.extendedState = JFrame.NORMAL
        } else {
            // 当前不是全屏状态，进入全屏
            frame.extendedState = JFrame.MAXIMIZED_BOTH
        }
    }


    private fun compileWorkspaceApk() {
        if (compileTask?.isAlive == true) {
            JOptionPane.showMessageDialog(
                mainWindow,
                I18n.translate(I18nKeys.ToolbarMessage.COMPILING),
                I18n.translate(I18nKeys.ToolbarMessage.COMPILING_TITLE),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val workspaceRoot = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()
        if (workspaceRoot == null) {
            JOptionPane.showMessageDialog(
                mainWindow,
                I18n.translate(I18nKeys.ToolbarMessage.WORKSPACE_NOT_OPENED),
                I18n.translate(I18nKeys.Dialog.TIP),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val apktoolConfig = File(workspaceRoot, "apktool.yml")
        if (!apktoolConfig.exists()) {
            JOptionPane.showMessageDialog(
                mainWindow,
                I18n.translate(I18nKeys.ToolbarMessage.NOT_APKTOOL_DIR),
                I18n.translate(I18nKeys.ToolbarMessage.CANNOT_COMPILE),
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val distDir = File(workspaceRoot, "dist").apply { mkdirs() }
        val baseName = workspaceRoot.name.ifEmpty { "output" }
        var outputApk = File(distDir, "${baseName}-recompiled.apk")
        var index = 1
        while (outputApk.exists()) {
            outputApk = File(distDir, "${baseName}-recompiled-$index.apk")
            index++
        }

        mainWindow.statusBar.showProgress(I18n.translate(I18nKeys.ToolbarMessage.COMPILING_APK), indeterminate = true)
        compileTask = Thread {
            try {
                val buildResult = ApkTool.build(workspaceRoot, outputApk)

                var signResult: SignResult? = null
                if (buildResult.status == ApkTool.Status.SUCCESS) {
                    SwingUtilities.invokeLater {
                        mainWindow.statusBar.showProgress(
                            I18n.translate(I18nKeys.ToolbarMessage.SIGNING_APK),
                            indeterminate = true
                        )
                    }
                    signResult = signWithDebugKeystore(outputApk)
                }

                val finalSignResult = signResult
                SwingUtilities.invokeLater {
                    mainWindow.statusBar.hideProgress()
                    when (buildResult.status) {
                        ApkTool.Status.SUCCESS -> {
                            if (finalSignResult?.success == true) {
                                mainWindow.statusBar.showSuccess(
                                    I18n.translate(I18nKeys.ToolbarMessage.COMPILE_AND_SIGN_SUCCESS)
                                        .format(outputApk.name)
                                )
                                JOptionPane.showMessageDialog(
                                    mainWindow,
                                    I18n.translate(I18nKeys.ToolbarMessage.APK_GENERATED)
                                        .format(outputApk.absolutePath),
                                    I18n.translate(I18nKeys.ToolbarMessage.COMPILE_COMPLETE),
                                    JOptionPane.INFORMATION_MESSAGE
                                )
                            } else {
                                val msg =
                                    finalSignResult?.message ?: I18n.translate(I18nKeys.ToolbarMessage.SIGN_EXCEPTION)
                                mainWindow.statusBar.showError(
                                    I18n.translate(I18nKeys.ToolbarMessage.SIGN_FAILED).format(msg)
                                )
                                JOptionPane.showMessageDialog(
                                    mainWindow,
                                    I18n.translate(I18nKeys.ToolbarMessage.SIGN_FAILED_DETAIL).format(msg),
                                    I18n.translate(I18nKeys.ToolbarMessage.SIGN_FAILED).split(":")[0],
                                    JOptionPane.ERROR_MESSAGE
                                )
                            }
                        }

                        ApkTool.Status.NOT_FOUND -> {
                            mainWindow.statusBar.showError(I18n.translate(I18nKeys.ToolbarMessage.APKTOOL_NOT_FOUND))
                            JOptionPane.showMessageDialog(
                                mainWindow,
                                I18n.translate(I18nKeys.ToolbarMessage.APKTOOL_NOT_FOUND_DETAIL),
                                I18n.translate(I18nKeys.ToolbarMessage.CANNOT_COMPILE),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }

                        ApkTool.Status.CANCELLED -> {
                            mainWindow.statusBar.setMessage(I18n.translate(I18nKeys.ToolbarMessage.COMPILE_CANCELLED))
                        }

                        ApkTool.Status.FAILED -> {
                            mainWindow.statusBar.showError(
                                I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED).format(buildResult.exitCode)
                            )
                            JOptionPane.showMessageDialog(
                                mainWindow,
                                I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED_DETAIL)
                                    .format(buildResult.exitCode, buildResult.output),
                                I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED).split("(")[0].trim(),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    mainWindow.statusBar.hideProgress()
                    mainWindow.statusBar.showError(
                        "${
                            I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED).split("(")[0].trim()
                        }: ${e.message}"
                    )
                    JOptionPane.showMessageDialog(
                        mainWindow,
                        I18n.translate(I18nKeys.ToolbarMessage.COMPILE_EXCEPTION).format(e.message ?: ""),
                        I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED).split("(")[0].trim(),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } finally {
                compileTask = null
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun signWithDebugKeystore(apkFile: File): SignResult {
        val keystore = ensureDebugKeystore()
            ?: return SignResult(false, I18n.translate(I18nKeys.ToolbarMessage.KEYSTORE_NOT_FOUND))
        val apksigner = locateApkSigner()
            ?: return SignResult(
                false,
                I18n.translate(I18nKeys.ToolbarMessage.APKSIGNER_NOT_FOUND)
            )

        val processBuilder =
            ProcessBuilder(
                apksigner,
                "sign",
                "--ks",
                keystore.absolutePath,
                "--ks-pass",
                "pass:android",
                "--key-pass",
                "pass:android",
                "--ks-key-alias",
                "androiddebugkey",
                apkFile.absolutePath
            )
        processBuilder.redirectErrorStream(true)
        return try {
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) SignResult(true, null)
            else SignResult(false, "apksigner exit code $exitCode\n$output")
        } catch (e: Exception) {
            SignResult(false, e.message ?: I18n.translate(I18nKeys.ToolbarMessage.SIGN_EXCEPTION))
        }
    }

    private fun ensureDebugKeystore(): File? {
        val keystore = File(System.getProperty("user.home"), ".android/debug.keystore")
        if (keystore.exists()) return keystore

        keystore.parentFile?.mkdirs()
        val keytool = locateKeytool() ?: return null
        val processBuilder =
            ProcessBuilder(
                keytool,
                "-genkeypair",
                "-alias",
                "androiddebugkey",
                "-keypass",
                "android",
                "-keystore",
                keystore.absolutePath,
                "-storepass",
                "android",
                "-dname",
                "CN=Android Debug,O=Android,C=US",
                "-validity",
                "9999",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048"
            )
        processBuilder.redirectErrorStream(true)
        return try {
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0 && keystore.exists()) {
                keystore
            } else {
                logger.warn("keytool 生成调试签名失败，输出: {}", output)
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun locateKeytool(): String? {
        try {
            val process = ProcessBuilder("keytool", "-help").start()
            process.waitFor()
            return "keytool"
        } catch (_: Exception) {
        }

        val javaHome = System.getProperty("java.home")
        if (!javaHome.isNullOrEmpty()) {
            val bin = File(javaHome, "bin/keytool")
            if (bin.exists()) return bin.absolutePath
            val binWin = File(javaHome, "bin/keytool.exe")
            if (binWin.exists()) return binWin.absolutePath
        }
        return null
    }

    private fun locateApkSigner(): String? {
        val projectRoot = File(System.getProperty("user.dir"))
        val local = File(projectRoot, "tools/apksigner")
        if (local.exists() && local.canExecute()) return local.absolutePath

        try {
            val process = ProcessBuilder("apksigner", "--version").start()
            if (process.waitFor() == 0) return "apksigner"
        } catch (_: Exception) {
        }

        val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (!sdkRoot.isNullOrBlank()) {
            val buildTools = File(sdkRoot, "build-tools")
            if (buildTools.isDirectory) {
                val candidates = buildTools.listFiles()?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
                if (candidates != null) {
                    for (dir in candidates) {
                        val exe = File(dir, "apksigner")
                        if (exe.exists()) return exe.absolutePath
                        val exeWin = File(dir, "apksigner.bat")
                        if (exeWin.exists()) return exeWin.absolutePath
                    }
                }
            }
        }
        return null
    }

    private data class SignResult(val success: Boolean, val message: String?)

}



