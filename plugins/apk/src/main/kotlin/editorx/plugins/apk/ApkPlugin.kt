package editorx.plugins.apk

import editorx.core.gui.GuiExtension
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.*
import editorx.core.service.BuildService
import editorx.core.util.IconRef
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import javax.swing.JOptionPane
import javax.swing.Timer

/**
 * APK 插件：提供 APK 项目快速跳转功能和构建能力
 */
class ApkPlugin : Plugin {
    companion object {
        private val logger = LoggerFactory.getLogger(ApkPlugin::class.java)
    }

    override fun getInfo() = PluginInfo(
        id = "apk",
        name = "APK",
        version = "0.0.1",
    )

    private var workspaceCheckTimer: Timer? = null
    private var lastWorkspaceState: Boolean = false
    private var toolbarRegistered: Boolean = false
    private var pluginContext: PluginContext? = null
    private val buildProviders = mutableListOf<BuildService>()

    override fun activate(pluginContext: PluginContext) {
        this.pluginContext = pluginContext

        // 创建并注册 BuildProvider 服务
        buildProviders.clear()
        buildProviders += ApkBuildService()
        buildProviders += XapkBuildService()
        buildProviders += AabBuildService()
        buildProviders.forEach { pluginContext.registerService(BuildService::class.java, it) }

        val gui = pluginContext.gui() ?: return

        // 初始时根据工作区状态决定是否注册工具栏按钮
        refreshWorkspaceState(gui)
        // 启动定时器，定期检查工作区状态
        startWorkspaceStateChecker(gui)

        // 注册 APK/XAPK/AAB 文件处理器
        pluginContext.gui()?.registerFileHandler(ApkFileHandler(gui))
        pluginContext.gui()?.registerFileHandler(XapkFileHandler(gui))
        pluginContext.gui()?.registerFileHandler(AabFileHandler(gui))
    }

    override fun deactivate() {
        // 取消注册 BuildProvider 服务
        buildProviders.forEach { provider ->
            pluginContext?.unregisterService(BuildService::class.java, provider)
        }
        buildProviders.clear()

        // 取消注册文件处理器
        pluginContext?.gui()?.unregisterAllFileHandlers()
        pluginContext?.gui()?.unregisterAllToolBarItems()
        toolbarRegistered = false

        pluginContext = null

        // 停止定时器
        workspaceCheckTimer?.stop()
        workspaceCheckTimer = null
    }

    /**
     * 启动工作区状态检查器
     */
    private fun startWorkspaceStateChecker(gui: GuiExtension) {
        workspaceCheckTimer = Timer(500) { // 每 500ms 检查一次
            val isApkWorkspace = isApkWorkspace(gui.getWorkspaceRoot())
            if (isApkWorkspace != lastWorkspaceState) {
                lastWorkspaceState = isApkWorkspace
                refreshWorkspaceState(gui)
            }
        }
        workspaceCheckTimer?.isRepeats = true
        workspaceCheckTimer?.start()
    }

    /**
     * 更新 ToolBar 按钮的启用/禁用状态
     */
    private fun updateToolBarButtonsState(gui: GuiExtension, enabled: Boolean) {
        if (!toolbarRegistered) return
        gui.setToolBarItemEnabled("apk.manifest", enabled)
        gui.setToolBarItemEnabled("apk.mainactivity", enabled)
        gui.setToolBarItemEnabled("apk.application", enabled)
        gui.setToolBarItemEnabled("apk.appinfo", enabled)
    }

    private fun refreshWorkspaceState(gui: GuiExtension) {
        val isApkWorkspace = isApkWorkspace(gui.getWorkspaceRoot())
        if (isApkWorkspace && !toolbarRegistered) {
            registerToolBarItems(gui)
            toolbarRegistered = true
        } else if (!isApkWorkspace && toolbarRegistered) {
            gui.unregisterAllToolBarItems()
            toolbarRegistered = false
        }
        updateToolBarButtonsState(gui, isApkWorkspace)
    }

    private fun registerToolBarItems(gui: GuiExtension) {
        // 注册 AndroidManifest.xml 跳转按钮
        gui.addToolBarItem(
            id = "apk.manifest",
            iconRef = IconRef("icons/android-manifest.svg", ApkPlugin::class.java.classLoader),
            text = I18n.translate(I18nKeys.Toolbar.GOTO_MANIFEST),
            action = {
                navigateToAndroidManifest(gui)
            }
        )

        // 注册 MainActivity 跳转按钮
        gui.addToolBarItem(
            id = "apk.mainactivity",
            iconRef = IconRef("icons/android-main-activity.svg", ApkPlugin::class.java.classLoader),
            text = I18n.translate(I18nKeys.Toolbar.GOTO_MAIN_ACTIVITY),
            action = {
                navigateToMainActivity(gui)
            }
        )

        // 注册 Application 跳转按钮
        gui.addToolBarItem(
            id = "apk.application",
            iconRef = IconRef("icons/android-application.svg", ApkPlugin::class.java.classLoader),
            text = I18n.translate(I18nKeys.Toolbar.GOTO_APPLICATION),
            action = {
                navigateToApplication(gui)
            }
        )

        // 注册 App 信息快捷修改入口
        gui.addToolBarItem(
            id = "apk.appinfo",
            iconRef = IconRef("icons/android-app-info.svg", ApkPlugin::class.java.classLoader),
            text = I18n.translate(I18nKeys.Toolbar.EDIT_APP_INFO),
            action = {
                AppInfoDialog.show(gui)
            }
        )
    }

    private fun isApkWorkspace(workspaceRoot: File?): Boolean {
        if (workspaceRoot == null) return false
        return File(workspaceRoot, "apktool.yml").isFile
    }

    /**
     * 跳转到 AndroidManifest.xml
     */
    private fun navigateToAndroidManifest(gui: GuiExtension) {
        val workspaceRoot = gui.getWorkspaceRoot()
        if (workspaceRoot == null) {
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.WORKSPACE_NOT_OPENED),
                I18n.translate(I18nKeys.Dialog.TIP),
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val manifestFile = File(workspaceRoot, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.MANIFEST_NOT_FOUND).format(manifestFile.absolutePath),
                I18n.translate(I18nKeys.Dialog.FILE_NOT_EXISTS),
                javax.swing.JOptionPane.WARNING_MESSAGE
            )
            return
        }

        gui.openFile(manifestFile)
    }

    /**
     * 跳转到 MainActivity
     */
    private fun navigateToMainActivity(gui: GuiExtension) {
        val workspaceRoot = gui.getWorkspaceRoot()
        if (workspaceRoot == null) {
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.WORKSPACE_NOT_OPENED),
                I18n.translate(I18nKeys.Dialog.TIP),
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val manifestFile = File(workspaceRoot, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.MAINACTIVITY_NOT_FOUND),
                I18n.translate(I18nKeys.Dialog.FILE_NOT_EXISTS),
                javax.swing.JOptionPane.WARNING_MESSAGE
            )
            return
        }

        try {
            val manifestContent = Files.readString(manifestFile.toPath())
            val mainActivityClass = findMainActivityClass(manifestContent)

            if (mainActivityClass == null) {
                showMessage(
                    I18n.translate(I18nKeys.ToolbarMessage.MAINACTIVITY_NOT_FOUND_DETAIL),
                    I18n.translate(I18nKeys.Dialog.NOT_FOUND),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE
                )
                return
            }

            val smaliFile = findSmaliFile(workspaceRoot, mainActivityClass)
            if (smaliFile != null && smaliFile.exists()) {
                gui.openFile(smaliFile)
            } else {
                showMessage(
                    I18n.translate(I18nKeys.ToolbarMessage.MAINACTIVITY_SMALI_NOT_FOUND).format(
                        mainActivityClass,
                        smaliFile?.absolutePath ?: I18n.translate(I18nKeys.Dialog.NOT_FOUND)
                    ),
                    I18n.translate(I18nKeys.Dialog.FILE_NOT_EXISTS),
                    javax.swing.JOptionPane.WARNING_MESSAGE
                )
            }
        } catch (e: Exception) {
            logger.error("跳转到 MainActivity 失败", e)
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.PARSE_MANIFEST_FAILED).format(e.message ?: ""),
                I18n.translate(I18nKeys.Dialog.ERROR),
                javax.swing.JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * 跳转到 Application
     */
    private fun navigateToApplication(gui: GuiExtension) {
        val workspaceRoot = gui.getWorkspaceRoot()
        if (workspaceRoot == null) {
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.WORKSPACE_NOT_OPENED),
                I18n.translate(I18nKeys.Dialog.TIP),
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val manifestFile = File(workspaceRoot, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.APPLICATION_NOT_FOUND),
                I18n.translate(I18nKeys.Dialog.FILE_NOT_EXISTS),
                javax.swing.JOptionPane.WARNING_MESSAGE
            )
            return
        }

        try {
            val manifestContent = Files.readString(manifestFile.toPath())
            val applicationClass = findApplicationClass(manifestContent)

            if (applicationClass == null) {
                showMessage(
                    I18n.translate(I18nKeys.ToolbarMessage.APPLICATION_NOT_FOUND_DETAIL),
                    I18n.translate(I18nKeys.Dialog.NOT_FOUND),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE
                )
                return
            }

            val smaliFile = findSmaliFile(workspaceRoot, applicationClass)
            if (smaliFile != null && smaliFile.exists()) {
                gui.openFile(smaliFile)
            } else {
                showMessage(
                    I18n.translate(I18nKeys.ToolbarMessage.APPLICATION_SMALI_NOT_FOUND)
                        .format(applicationClass, smaliFile?.absolutePath ?: I18n.translate(I18nKeys.Dialog.NOT_FOUND)),
                    I18n.translate(I18nKeys.Dialog.FILE_NOT_EXISTS),
                    javax.swing.JOptionPane.WARNING_MESSAGE
                )
            }
        } catch (e: Exception) {
            logger.error("跳转到 Application 失败", e)
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.PARSE_MANIFEST_FAILED).format(e.message ?: ""),
                I18n.translate(I18nKeys.Dialog.ERROR),
                javax.swing.JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * 显示消息对话框（使用 SwingUtilities.invokeLater 确保在 EDT 上执行）
     */
    private fun showMessage(message: String, title: String, messageType: Int) {
        javax.swing.SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(null, message, title, messageType)
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
