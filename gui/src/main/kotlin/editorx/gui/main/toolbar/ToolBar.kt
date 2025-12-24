package editorx.gui.main.toolbar

import editorx.core.toolchain.ApkTool
import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import editorx.gui.main.MainWindow
import editorx.gui.main.explorer.Explorer
import editorx.gui.main.navigationbar.NavigationBar
import editorx.gui.settings.SettingsDialog
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.ActionListener
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.swing.*

/**
 * 顶部工具栏：常用操作的快捷入口。
 * 在 macOS 上菜单集成到系统顶栏时，窗口内仍保留此工具栏以便操作。
 */
class ToolBar(private val mainWindow: MainWindow) : JToolBar() {
    companion object {
        private val logger = LoggerFactory.getLogger(ToolBar::class.java)
        private const val ICON_SIZE = 18
    }

    private val navigationBar = NavigationBar(mainWindow)
    private var toggleSideBarButton: JButton? = null
    private var compileTask: Thread? = null
    private var vcsWidgetLabel: JLabel? = null
    private var vcsWidgetIconLabel: JLabel? = null

    init {
        isFloatable = false
        val separator = Color(0xDE, 0xDE, 0xDE)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, separator),
            BorderFactory.createEmptyBorder(2, 5, 2, 5),
        )
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        buildActions()

        // 添加双击空白处全屏功能
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && e.button == java.awt.event.MouseEvent.BUTTON1) {
                    // 检查是否点击在空白区域（不是按钮上）
                    if (e.source == this@ToolBar) {
                        toggleFullScreen()
                    }
                }
            }
        })
    }

    private fun isMacOS(): Boolean {
        return System.getProperty("os.name").lowercase().contains("mac")
    }

    /**
     * 设置 VCS Widget（参考 IDEA/Android Studio 的效果）
     */
    private fun setupVcsWidget() {
        // 创建一个面板，包含图标、文字标签和下拉箭头
        val widgetPanel = JPanel(BorderLayout()).apply {
            // 设置边框和样式，使其看起来像一个下拉框
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0xCC, 0xCC, 0xCC), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
            )
            maximumSize = Dimension(300, 28)
            minimumSize = Dimension(100, 28)
            background = Color.WHITE

            // 创建鼠标监听器（用于显示弹出菜单和悬停效果）
            val panel = this
            val mouseListener = object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        showVcsPopupMenu(panel)
                    }
                }

                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    panel.background = Color(0xF5, 0xF5, 0xF5)
                    panel.repaint()
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    panel.background = Color.WHITE
                    panel.repaint()
                }
            }

            // 左侧容器：图标 + 文字
            val leftPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                // 添加鼠标监听器，使点击文字和图标也能弹出菜单
                addMouseListener(mouseListener)

                // 图标标签（版本控制图标）
                val iconLabel = JLabel().apply {
                    preferredSize = Dimension(16, 16)
                    maximumSize = Dimension(16, 16)
                    minimumSize = Dimension(16, 16)
                    horizontalAlignment = SwingConstants.CENTER
                    verticalAlignment = SwingConstants.CENTER
                    // 添加鼠标监听器
                    addMouseListener(mouseListener)
                }
                vcsWidgetIconLabel = iconLabel
                add(iconLabel)
                add(Box.createHorizontalStrut(6))

                // 文字标签
                val textLabel = JLabel().apply {
                    font = font.deriveFont(java.awt.Font.PLAIN, 13f)
                    horizontalAlignment = SwingConstants.LEFT
                    // 添加鼠标监听器
                    addMouseListener(mouseListener)
                }
                vcsWidgetLabel = textLabel
                add(textLabel)
            }
            add(leftPanel, BorderLayout.CENTER)

            // 下拉箭头图标（右侧）
            val arrowLabel = JLabel("▼").apply {
                font = font.deriveFont(java.awt.Font.PLAIN, 10f)
                foreground = Color(0x66, 0x66, 0x66)
                horizontalAlignment = SwingConstants.CENTER
                preferredSize = Dimension(16, 16)
                // 添加鼠标监听器，使点击箭头也能弹出菜单
                addMouseListener(mouseListener)
            }
            add(arrowLabel, BorderLayout.EAST)

            // 添加鼠标监听器到整个面板
            addMouseListener(mouseListener)

            // 初始更新显示
            updateVcsDisplay()
        }
        add(widgetPanel)
    }

    /**
     * 更新 VCS Widget 的显示内容（显示 git 分支或"版本控制"）
     */
    fun updateVcsDisplay() {
        val label = vcsWidgetLabel ?: return
        val iconLabel = vcsWidgetIconLabel ?: return
        val workspaceRoot = mainWindow.guiControl.workspace.getWorkspaceRoot()

        label.text = "版本控制"
        iconLabel.icon = null

        if (workspaceRoot == null || !workspaceRoot.exists()) {
            return
        }

        // 在后台线程中获取 git 分支
        Thread {
            try {
                val branchName = getCurrentGitBranch(workspaceRoot)
                SwingUtilities.invokeLater {
                    // 设置图标（git 图标）
                    iconLabel.icon = loadVcsIcon()

                    if (branchName != null) {
                        // 是 git 仓库，显示分支名称
                        label.text = branchName
                    }
                }
            } catch (e: Exception) {
                logger.debug("获取 git 分支失败", e)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    /**
     * 加载版本控制图标
     */
    private fun loadVcsIcon(): Icon? {
        return try {
            // 尝试从主资源加载
            IconLoader.getIcon(IconRef("icons/git-branch.svg"), 14)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 兼容性方法：保持向后兼容
     */
    @Deprecated("使用 updateVcsDisplay() 代替", ReplaceWith("updateVcsDisplay()"))
    fun updateProjectDisplay() {
        updateVcsDisplay()
    }

    /**
     * 获取当前 git 分支名称
     * @return 分支名称，如果不是 git 仓库或获取失败则返回 null
     */
    private fun getCurrentGitBranch(workspaceRoot: File): String? {
        try {
            // 检查是否是 git 仓库（.git 可能是目录或文件）
            val gitFile = File(workspaceRoot, ".git")
            if (!gitFile.exists()) {
                logger.debug("工作区不是 git 仓库: {}", workspaceRoot.absolutePath)
                return null
            }

            // 方法1：使用 git rev-parse --abbrev-ref HEAD 获取当前分支名称
            try {
                val process1 = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                    .directory(workspaceRoot)
                    .redirectErrorStream(true)
                    .start()

                val output1 = process1.inputStream.bufferedReader().use { it.readText() }.trim()
                val exitCode1 = process1.waitFor()

                if (exitCode1 == 0 && output1.isNotBlank() && output1 != "HEAD") {
                    logger.debug("获取 git 分支成功 (rev-parse): {}", output1)
                    return output1
                }

                // 如果是 detached HEAD，output1 会是 "HEAD"
                if (output1 == "HEAD") {
                    logger.debug("处于 detached HEAD 状态")
                }
            } catch (e: Exception) {
                logger.debug("git rev-parse 命令失败", e)
            }

            // 方法2：使用 git branch --show-current（备用方案）
            try {
                val process2 = ProcessBuilder("git", "branch", "--show-current")
                    .directory(workspaceRoot)
                    .redirectErrorStream(true)
                    .start()

                val output2 = process2.inputStream.bufferedReader().use { it.readText() }.trim()
                val exitCode2 = process2.waitFor()

                if (exitCode2 == 0 && output2.isNotBlank()) {
                    logger.debug("获取 git 分支成功 (branch --show-current): {}", output2)
                    return output2
                }
            } catch (e: Exception) {
                logger.debug("git branch --show-current 命令失败", e)
            }

            logger.debug("无法获取 git 分支名称，但工作区是 git 仓库")
        } catch (e: Exception) {
            logger.warn("执行 git 命令时发生异常", e)
        }
        return null
    }

    /**
     * 显示 VCS 弹出菜单（点击 VCS Widget 时）
     */
    private fun showVcsPopupMenu(invoker: java.awt.Component) {
        val workspaceRoot = mainWindow.guiControl.workspace.getWorkspaceRoot()
        val popupMenu = JPopupMenu()

        if (workspaceRoot != null && workspaceRoot.exists()) {
            // 获取当前分支（同步获取，用于菜单显示）
            val branchName = getCurrentGitBranchSync(workspaceRoot)

            if (branchName != null) {
                // 显示当前分支
                popupMenu.add(JMenuItem("分支: $branchName").apply {
                    isEnabled = false
                    font = font.deriveFont(font.size - 1f)
                    foreground = Color.GRAY
                })
            } else {
                // 显示当前项目路径（非 git 仓库时）
                popupMenu.add(JMenuItem("路径: ${workspaceRoot.absolutePath}").apply {
                    isEnabled = false
                    font = font.deriveFont(font.size - 1f)
                    foreground = Color.GRAY
                })
            }
            popupMenu.addSeparator()
        }

        // 添加"打开文件夹..."选项
        popupMenu.add(JMenuItem("打开文件夹...").apply {
            addActionListener {
                openFolder()
                updateVcsDisplay()
            }
        })

        // 显示弹出菜单，位置在项目选择框下方
        popupMenu.show(invoker, 0, invoker.height)
    }

    /**
     * 同步获取当前 git 分支名称（用于菜单显示，避免阻塞 UI）
     * @return 分支名称，如果不是 git 仓库或获取失败则返回 null
     */
    private fun getCurrentGitBranchSync(workspaceRoot: File): String? {
        return try {
            val gitFile = File(workspaceRoot, ".git")
            if (!gitFile.exists()) {
                return null
            }

            // 方法1：使用 git rev-parse --abbrev-ref HEAD
            try {
                val process1 = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                    .directory(workspaceRoot)
                    .redirectErrorStream(true)
                    .start()

                val output1 = process1.inputStream.bufferedReader().use { it.readText() }.trim()
                val exitCode1 = process1.waitFor()

                if (exitCode1 == 0 && output1.isNotBlank() && output1 != "HEAD") {
                    return output1
                }
            } catch (e: Exception) {
                // 忽略，尝试下一个方法
            }

            // 方法2：使用 git branch --show-current
            try {
                val process2 = ProcessBuilder("git", "branch", "--show-current")
                    .directory(workspaceRoot)
                    .redirectErrorStream(true)
                    .start()

                val output2 = process2.inputStream.bufferedReader().use { it.readText() }.trim()
                val exitCode2 = process2.waitFor()

                if (exitCode2 == 0 && output2.isNotBlank()) {
                    return output2
                }
            } catch (e: Exception) {
                // 忽略
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun JButton.compact(textLabel: String, l: ActionListener): JButton = apply {
        toolTipText = textLabel
        isFocusable = false
        margin = Insets(4, 4, 4, 4)
        addActionListener(l)
    }

    private fun buildActions() {
        setupLeftActions()
        add(Box.createHorizontalGlue())
        setupRightActions()
    }

    private fun setupLeftActions() {
        if (isMacOS()) {
            // macOS 模式：左侧留空（给系统控制按钮），中间显示标题，右侧显示按钮
            add(Box.createHorizontalStrut(70)) // 为 macOS 交通灯按钮留空间
        }

        // VCS Widget（最左侧）
        setupVcsWidget()

        add(Box.createHorizontalStrut(12))

        // Android 项目快速跳转按钮
        add(
            JButton(
                IconLoader.getIcon(
                    IconRef("icons/android-manifest.svg"),
                    ICON_SIZE
                )
            ).compact("跳转到 AndroidManifest.xml") {
                navigateToAndroidManifest()
            })

        add(Box.createHorizontalStrut(6))

        add(JButton(IconLoader.getIcon(IconRef("icons/main-activity.svg"), ICON_SIZE)).compact("跳转到 MainActivity") {
            navigateToMainActivity()
        })

        add(Box.createHorizontalStrut(6))

        add(JButton(IconLoader.getIcon(IconRef("icons/application.svg"), ICON_SIZE)).compact("跳转到 Application") {
            navigateToApplication()
        })

        add(Box.createHorizontalStrut(12))
    }

    private fun setupRightActions() {
        add(JButton(IconLoader.getIcon(IconRef("icons/build.svg"), ICON_SIZE)).compact("构建") {
            compileWorkspaceApk()
        })

        add(Box.createHorizontalStrut(12))
        addSeparator()
        add(Box.createHorizontalStrut(12))

        toggleSideBarButton = JButton(getSideBarIcon()).compact("切换侧边栏") {
            toggleSideBar()
        }
        add(toggleSideBarButton)

        add(Box.createHorizontalStrut(6))

        add(JButton(IconLoader.getIcon(IconRef("icons/settings.svg"), ICON_SIZE)).compact("设置") {
            showSettings()
        })

        add(Box.createHorizontalStrut(12))
    }

    private fun openFolder() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "选择文件夹"
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val selected = chooser.selectedFile
            mainWindow.guiControl.workspace.openWorkspace(selected)
            (mainWindow.sideBar.getView("explorer") as? Explorer)?.refreshRoot()
            updateProjectDisplay()
        }
    }

    fun updateSideBarIcon(sideBarVisible: Boolean) {
        val iconName = if (sideBarVisible) "icons/layout-sidebar-left.svg" else "icons/layout-sidebar-left-off.svg"
        toggleSideBarButton?.icon = IconLoader.getIcon(IconRef(iconName), ICON_SIZE)
    }

    private fun getSideBarIcon(): Icon? {
        val isVisible = mainWindow.sideBar.isSideBarVisible()
        val iconName = if (isVisible) "icons/layout-sidebar-left.svg" else "icons/layout-sidebar-left-off.svg"
        return IconLoader.getIcon(IconRef(iconName), ICON_SIZE)
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
        val pm = mainWindow.pluginManager ?: run {
            JOptionPane.showMessageDialog(this, "插件系统尚未初始化", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        SettingsDialog(mainWindow, mainWindow.guiControl, pm, SettingsDialog.Section.APPEARANCE).isVisible = true
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
                this,
                "正在编译，请稍候…",
                "编译进行中",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val workspaceRoot = mainWindow.guiControl.workspace.getWorkspaceRoot()
        if (workspaceRoot == null) {
            JOptionPane.showMessageDialog(this, "尚未打开工作区", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val apktoolConfig = File(workspaceRoot, "apktool.yml")
        if (!apktoolConfig.exists()) {
            JOptionPane.showMessageDialog(
                this,
                "当前工作区不是 apktool 反编译目录（缺少 apktool.yml）",
                "无法编译",
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

        mainWindow.statusBar.showProgress("正在编译APK...", indeterminate = true)
        compileTask = Thread {
            try {
                val buildResult = ApkTool.build(workspaceRoot, outputApk)

                var signResult: SignResult? = null
                if (buildResult.status == ApkTool.Status.SUCCESS) {
                    SwingUtilities.invokeLater {
                        mainWindow.statusBar.showProgress("正在签名APK...", indeterminate = true)
                    }
                    signResult = signWithDebugKeystore(outputApk)
                }

                val finalSignResult = signResult
                SwingUtilities.invokeLater {
                    mainWindow.statusBar.hideProgress()
                    when (buildResult.status) {
                        ApkTool.Status.SUCCESS -> {
                            if (finalSignResult?.success == true) {
                                mainWindow.statusBar.showSuccess("APK 编译并签名完成: ${outputApk.name}")
                                JOptionPane.showMessageDialog(
                                    this@ToolBar,
                                    "已生成并使用调试证书签名的 APK:\n${outputApk.absolutePath}",
                                    "编译完成",
                                    JOptionPane.INFORMATION_MESSAGE
                                )
                            } else {
                                val msg = finalSignResult?.message ?: "未知原因"
                                mainWindow.statusBar.showError("签名失败: $msg")
                                JOptionPane.showMessageDialog(
                                    this@ToolBar,
                                    "APK 编译成功，但签名失败:\n$msg",
                                    "签名失败",
                                    JOptionPane.ERROR_MESSAGE
                                )
                            }
                        }

                        ApkTool.Status.NOT_FOUND -> {
                            mainWindow.statusBar.showError("未找到 apktool")
                            JOptionPane.showMessageDialog(
                                this@ToolBar,
                                "未找到 apktool，可执行文件需放在 toolchain/apktool 或 tools/apktool，或加入 PATH",
                                "无法编译",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }

                        ApkTool.Status.CANCELLED -> {
                            mainWindow.statusBar.setMessage("APK 编译被取消", persistent = true)
                        }

                        ApkTool.Status.FAILED -> {
                            mainWindow.statusBar.showError("APK 编译失败 (exit=${buildResult.exitCode})")
                            JOptionPane.showMessageDialog(
                                this@ToolBar,
                                "apktool 编译失败 (exit=${buildResult.exitCode})\n${buildResult.output}",
                                "编译失败",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    mainWindow.statusBar.hideProgress()
                    mainWindow.statusBar.showError("APK 编译失败: ${e.message}")
                    JOptionPane.showMessageDialog(
                        this@ToolBar,
                        "编译过程中出现异常: ${e.message}",
                        "编译失败",
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
            ?: return SignResult(false, "未找到或无法创建 ~/.android/debug.keystore")
        val apksigner = locateApkSigner()
            ?: return SignResult(
                false,
                "未找到 apksigner，请设置 ANDROID_HOME/ANDROID_SDK_ROOT 或将 apksigner 加入 PATH"
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
            else SignResult(false, "apksigner 退出码 $exitCode\n$output")
        } catch (e: Exception) {
            SignResult(false, e.message ?: "签名时发生未知错误")
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

    // Android 项目快速跳转方法

    /**
     * 跳转到 AndroidManifest.xml
     */
    private fun navigateToAndroidManifest() {
        val workspaceRoot = mainWindow.guiControl.workspace.getWorkspaceRoot()
        if (workspaceRoot == null) {
            JOptionPane.showMessageDialog(this, "尚未打开工作区", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val manifestFile = File(workspaceRoot, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            JOptionPane.showMessageDialog(
                this,
                "未找到 AndroidManifest.xml 文件\n路径: ${manifestFile.absolutePath}",
                "文件不存在",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        mainWindow.editor.openFile(manifestFile)
    }

    /**
     * 跳转到 MainActivity
     */
    private fun navigateToMainActivity() {
        val workspaceRoot = mainWindow.guiControl.workspace.getWorkspaceRoot()
        if (workspaceRoot == null) {
            JOptionPane.showMessageDialog(this, "尚未打开工作区", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val manifestFile = File(workspaceRoot, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            JOptionPane.showMessageDialog(
                this,
                "未找到 AndroidManifest.xml 文件，无法定位 MainActivity",
                "文件不存在",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        try {
            val manifestContent = Files.readString(manifestFile.toPath())
            val mainActivityClass = findMainActivityClass(manifestContent)

            if (mainActivityClass == null) {
                JOptionPane.showMessageDialog(
                    this,
                    "在 AndroidManifest.xml 中未找到 MainActivity（未找到包含 MAIN action 的 Activity）",
                    "未找到",
                    JOptionPane.INFORMATION_MESSAGE
                )
                return
            }

            val smaliFile = findSmaliFile(workspaceRoot, mainActivityClass)
            if (smaliFile != null && smaliFile.exists()) {
                mainWindow.editor.openFile(smaliFile)
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "未找到 MainActivity 对应的 smali 文件\n类名: $mainActivityClass\n预期路径: ${smaliFile?.absolutePath ?: "未知"}",
                    "文件不存在",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        } catch (e: Exception) {
            logger.error("跳转到 MainActivity 失败", e)
            JOptionPane.showMessageDialog(
                this,
                "解析 AndroidManifest.xml 失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * 跳转到 Application
     */
    private fun navigateToApplication() {
        val workspaceRoot = mainWindow.guiControl.workspace.getWorkspaceRoot()
        if (workspaceRoot == null) {
            JOptionPane.showMessageDialog(this, "尚未打开工作区", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val manifestFile = File(workspaceRoot, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            JOptionPane.showMessageDialog(
                this,
                "未找到 AndroidManifest.xml 文件，无法定位 Application",
                "文件不存在",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        try {
            val manifestContent = Files.readString(manifestFile.toPath())
            val applicationClass = findApplicationClass(manifestContent)

            if (applicationClass == null) {
                JOptionPane.showMessageDialog(
                    this,
                    "在 AndroidManifest.xml 中未找到自定义 Application 类（使用默认 Application）",
                    "未找到",
                    JOptionPane.INFORMATION_MESSAGE
                )
                return
            }

            val smaliFile = findSmaliFile(workspaceRoot, applicationClass)
            if (smaliFile != null && smaliFile.exists()) {
                mainWindow.editor.openFile(smaliFile)
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "未找到 Application 对应的 smali 文件\n类名: $applicationClass\n预期路径: ${smaliFile?.absolutePath ?: "未知"}",
                    "文件不存在",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        } catch (e: Exception) {
            logger.error("跳转到 Application 失败", e)
            JOptionPane.showMessageDialog(
                this,
                "解析 AndroidManifest.xml 失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * 从 AndroidManifest.xml 内容中查找 MainActivity 类名
     * MainActivity 是指同时包含 MAIN action 和 LAUNCHER category 的 Activity（在同一个 intent-filter 中）
     */
    private fun findMainActivityClass(manifestContent: String): String? {
        try {
            // 获取包名（用于补全相对类名）
            val packageName = extractPackageName(manifestContent)

            // 使用更准确的方法查找所有 activity 标签
            val activities = extractActivities(manifestContent)

            for (activity in activities) {
                // 首先提取 activity 的 android:name 属性
                val activityName = extractAttribute(activity, "android:name") ?: continue

                // 检查该 activity 是否包含标准的启动 intent-filter（MAIN + LAUNCHER）
                if (hasMainLauncherIntentFilter(activity)) {
                    var className = activityName
                    // 如果类名以 . 开头，需要补全包名
                    if (className.startsWith(".") && packageName != null) {
                        className = packageName + className
                    }
                    return className
                }
            }
        } catch (e: Exception) {
            logger.warn("查找 MainActivity 类名失败", e)
        }
        return null
    }

    /**
     * 提取 AndroidManifest.xml 中的包名
     */
    private fun extractPackageName(manifestContent: String): String? {
        val pattern = """<manifest[^>]+package\s*=\s*["']([^"']+)["']""".toRegex()
        val match = pattern.find(manifestContent)
        return match?.groupValues?.get(1)
    }

    /**
     * 从 AndroidManifest.xml 中提取所有 activity 标签的完整 XML（包括嵌套内容）
     */
    private fun extractActivities(manifestContent: String): List<String> {
        val activities = mutableListOf<String>()
        var pos = 0

        while (pos < manifestContent.length) {
            // 查找下一个 <activity 标签的开始位置
            val startTag = manifestContent.indexOf("<activity", pos)
            if (startTag == -1) break

            // 查找 activity 标签的开始标签结束位置（> 或 />）
            val tagEnd = findTagEnd(manifestContent, startTag)
            if (tagEnd == -1) break

            // 检查是否是自闭合标签
            val tagContent = manifestContent.substring(startTag, tagEnd + 1)
            if (tagContent.trimEnd().endsWith("/>")) {
                // 自闭合标签，直接添加
                activities.add(tagContent)
                pos = tagEnd + 1
                continue
            }

            // 查找对应的 </activity> 结束标签
            val endTag = findMatchingEndTag(manifestContent, tagEnd + 1, "activity")
            if (endTag == -1) {
                pos = tagEnd + 1
                continue
            }

            // 提取完整的 activity XML
            val activityXml = manifestContent.substring(startTag, endTag + "</activity>".length)
            activities.add(activityXml)
            pos = endTag + "</activity>".length
        }

        return activities
    }

    /**
     * 查找标签的结束位置（> 或 />）
     */
    private fun findTagEnd(content: String, start: Int): Int {
        var pos = start
        var inQuotes = false
        var quoteChar: Char? = null

        while (pos < content.length) {
            val ch = content[pos]
            when {
                !inQuotes && (ch == '"' || ch == '\'') -> {
                    inQuotes = true
                    quoteChar = ch
                }

                inQuotes && ch == quoteChar -> {
                    inQuotes = false
                    quoteChar = null
                }

                !inQuotes && ch == '>' -> {
                    return pos
                }
            }
            pos++
        }
        return -1
    }

    /**
     * 查找匹配的结束标签（处理嵌套标签）
     */
    private fun findMatchingEndTag(content: String, start: Int, tagName: String): Int {
        val startTag = "<$tagName"
        val endTag = "</$tagName>"
        var pos = start
        var depth = 1

        while (pos < content.length && depth > 0) {
            val nextStart = content.indexOf(startTag, pos)
            val nextEnd = content.indexOf(endTag, pos)

            when {
                nextEnd == -1 -> return -1 // 没找到结束标签
                nextStart != -1 && nextStart < nextEnd -> {
                    // 找到嵌套的开始标签
                    depth++
                    pos = nextStart + startTag.length
                }

                else -> {
                    // 找到结束标签
                    depth--
                    if (depth == 0) {
                        return nextEnd
                    }
                    pos = nextEnd + endTag.length
                }
            }
        }
        return -1
    }

    /**
     * 从 XML 片段中提取指定属性的值
     */
    private fun extractAttribute(xml: String, attrName: String): String? {
        // 匹配属性，支持单引号和双引号，处理空格
        val pattern = """$attrName\s*=\s*["']([^"']+)["']""".toRegex()
        val match = pattern.find(xml)
        return match?.groupValues?.get(1)
    }

    /**
     * 检查 activity 是否包含标准的启动 intent-filter（MAIN action + LAUNCHER category）
     * 需要在同一个 intent-filter 中同时包含这两个
     */
    private fun hasMainLauncherIntentFilter(activityXml: String): Boolean {
        // 提取所有 intent-filter 标签
        val intentFilters = extractIntentFilters(activityXml)

        for (intentFilter in intentFilters) {
            // 检查是否同时包含 MAIN action 和 LAUNCHER category
            val hasMainAction = """android\.intent\.action\.MAIN""".toRegex().containsMatchIn(intentFilter)
            val hasLauncherCategory = """android\.intent\.category\.LAUNCHER""".toRegex().containsMatchIn(intentFilter)

            if (hasMainAction && hasLauncherCategory) {
                return true
            }
        }
        return false
    }

    /**
     * 从 activity XML 中提取所有 intent-filter 标签
     */
    private fun extractIntentFilters(activityXml: String): List<String> {
        val intentFilters = mutableListOf<String>()
        var pos = 0

        while (pos < activityXml.length) {
            val startTag = activityXml.indexOf("<intent-filter", pos)
            if (startTag == -1) break

            val tagEnd = findTagEnd(activityXml, startTag)
            if (tagEnd == -1) break

            // 检查是否是自闭合标签（intent-filter 通常不是自闭合的，但为了安全起见还是检查）
            val tagContent = activityXml.substring(startTag, tagEnd + 1)
            if (tagContent.trimEnd().endsWith("/>")) {
                intentFilters.add(tagContent)
                pos = tagEnd + 1
                continue
            }

            // 查找对应的 </intent-filter> 结束标签
            val endTag = findMatchingEndTag(activityXml, tagEnd + 1, "intent-filter")
            if (endTag == -1) {
                pos = tagEnd + 1
                continue
            }

            val intentFilterXml = activityXml.substring(startTag, endTag + "</intent-filter>".length)
            intentFilters.add(intentFilterXml)
            pos = endTag + "</intent-filter>".length
        }

        return intentFilters
    }

    /**
     * 从 AndroidManifest.xml 内容中查找 Application 类名
     */
    private fun findApplicationClass(manifestContent: String): String? {
        try {
            // 查找 <application android:name="..."> 中的 android:name 属性
            val applicationPattern = """<application[^>]+android:name\s*=\s*["']([^"']+)["']""".toRegex()
            val match = applicationPattern.find(manifestContent)
            if (match != null) {
                var className = match.groupValues[1]
                // 如果类名以 . 开头，需要补全包名
                if (className.startsWith(".")) {
                    val packagePattern = """<manifest[^>]+package\s*=\s*["']([^"']+)["']""".toRegex()
                    val packageMatch = packagePattern.find(manifestContent)
                    if (packageMatch != null) {
                        className = packageMatch.groupValues[1] + className
                    }
                }
                return className
            }
        } catch (e: Exception) {
            logger.warn("查找 Application 类名失败", e)
        }
        return null
    }

    /**
     * 根据 Java 类名查找对应的 smali 文件
     * 将 com.example.MainActivity 转换为 smali/com/example/MainActivity.smali
     */
    private fun findSmaliFile(workspaceRoot: File, className: String): File? {
        // 将类名转换为路径（com.example.MainActivity -> com/example/MainActivity.smali）
        val path = className.replace(".", "/") + ".smali"

        // 在 smali 目录下查找（apktool 反编译后的常见结构）
        // 可能有多个 smali 目录（smali, smali_classes2, smali_classes3, ...）
        val smaliDirs = workspaceRoot.listFiles()?.filter {
            it.isDirectory && it.name.matches("""^smali(_classes\d+)?$""".toRegex())
        } ?: emptyList()

        // 先尝试 smali 目录
        for (smaliDir in smaliDirs.sorted()) {
            val smaliFile = File(smaliDir, path)
            if (smaliFile.exists()) {
                return smaliFile
            }
        }

        // 如果没找到，返回预期的路径（用于错误提示）
        val defaultSmaliDir = File(workspaceRoot, "smali")
        return File(defaultSmaliDir, path)
    }
}
