package editorx.gui.main.toolbar

import editorx.gui.IconRef
import editorx.gui.main.MainWindow
import editorx.gui.main.explorer.Explorer
import editorx.gui.dialog.PluginManagerDialog
import editorx.gui.main.navigationbar.NavigationBar
import editorx.gui.toolchain.ApkTool
import editorx.util.IconLoader
import java.awt.Insets
import java.awt.Color
import java.awt.event.ActionListener
import java.io.File
import java.util.Locale
import javax.swing.*

/**
 * 顶部工具栏：常用操作的快捷入口。
 * 在 macOS 上菜单集成到系统顶栏时，窗口内仍保留此工具栏以便操作。
 */
class ToolBar(private val mainWindow: MainWindow) : JToolBar() {
    companion object {
        private const val ICON_SIZE = 20
    }

    private val navigationBar = NavigationBar(mainWindow)
    private var toggleSideBarButton: JButton? = null
    private var compileTask: Thread? = null

    init {
        isFloatable = false
        val separator = Color(0xDE, 0xDE, 0xDE)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, separator),
            BorderFactory.createEmptyBorder(2, 5, 2, 5),
        )
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        buildActions()
    }

    fun updateNavigation(currentFile: File?) {
        navigationBar.update(currentFile)
    }

    private fun JButton.compact(textLabel: String, l: ActionListener): JButton = apply {
        toolTipText = textLabel
        isFocusable = false
        margin = Insets(4, 4, 4, 4)
        addActionListener(l)
    }

    private fun buildActions() {
        add(navigationBar)
        add(Box.createHorizontalStrut(8))
        add(Box.createHorizontalGlue())

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
        add(JButton(IconLoader.getIcon(IconRef("icons/settings.svg"), ICON_SIZE)).compact("设置") {
            showSettings()
        })
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

    private fun showPluginManager() {
        val pm = mainWindow.pluginManager ?: return
        PluginManagerDialog(mainWindow, pm).isVisible = true
    }

    private fun showFindDialog() {
        JOptionPane.showMessageDialog(this, "查找功能待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun showReplaceDialog() {
        JOptionPane.showMessageDialog(this, "替换功能待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun showSettings() {
        JOptionPane.showMessageDialog(this, "设置界面待实现", "提示", JOptionPane.INFORMATION_MESSAGE)
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
            ?: return SignResult(false, "未找到 apksigner，请设置 ANDROID_HOME/ANDROID_SDK_ROOT 或将 apksigner 加入 PATH")

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
                println("keytool 输出: $output")
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
                val candidates = buildTools.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
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
