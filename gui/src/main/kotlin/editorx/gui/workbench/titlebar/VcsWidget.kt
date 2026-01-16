package editorx.gui.workbench.titlebar

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import editorx.core.util.Store
import editorx.core.workspace.Workspace
import editorx.gui.settings.SettingsStoreKeys
import editorx.gui.theme.ThemeManager
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.*

/**
 * VCS Widget - 显示版本控制信息（如 git 分支）
 * 参考 IDEA/Android Studio 的效果
 */
class VcsWidget(
    private val workspace: Workspace,
    private val settings: Store
) : JPanel() {
    companion object {
        private val logger = LoggerFactory.getLogger(VcsWidget::class.java)
        private const val ICON_SIZE = 14
    }

    private val iconLabel = JLabel().apply {
        preferredSize = Dimension(ICON_SIZE, ICON_SIZE)
        maximumSize = Dimension(ICON_SIZE, ICON_SIZE)
        minimumSize = Dimension(ICON_SIZE, ICON_SIZE)
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment = SwingConstants.CENTER
    }

    private val textLabel = JLabel().apply {
        font = font.deriveFont(Font.PLAIN, 12f)
        horizontalAlignment = SwingConstants.LEFT
    }
    
    private val arrowLabel = JLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment = SwingConstants.CENTER
        preferredSize = Dimension(14, 14)
    }

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        maximumSize = Dimension(300, 24)
        minimumSize = Dimension(100, 24)
        isOpaque = false  // 透明背景（幽灵按钮样式）

        // 创建鼠标监听器（用于显示弹出菜单和悬停效果）
        val mouseListener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    showVcsPopupMenu(this@VcsWidget)
                }
            }

            override fun mouseEntered(e: MouseEvent) {
                // 悬停时保持透明，或使用半透明效果
                isOpaque = false
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                // 保持透明背景
                isOpaque = false
                repaint()
            }
        }

        // 图标标签（版本控制图标）
        iconLabel.addMouseListener(mouseListener)
        add(iconLabel)
        add(Box.createHorizontalStrut(4))

        // 文字标签
        textLabel.addMouseListener(mouseListener)
        add(textLabel)

        // 文字和箭头之间的间距（减小间距）
        add(Box.createHorizontalStrut(4))

        // 下拉箭头图标（右侧）
        arrowLabel.icon = IconLoader.getIcon(
            IconRef("icons/common/chevron-down.svg"), 
            14,
            adaptToTheme = true,
            getThemeColor = { ThemeManager.currentTheme.onSurface }
        )
        arrowLabel.addMouseListener(mouseListener)
        add(arrowLabel)

        // 添加鼠标监听器到整个面板
        addMouseListener(mouseListener)
        
        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateIcons() }

        // 初始更新显示
        updateDisplay()
    }
    
    /**
     * 更新图标以适配当前主题
     */
    private fun updateIcons() {
        arrowLabel.icon = IconLoader.getIcon(
            IconRef("icons/common/chevron-down.svg"), 
            14,
            adaptToTheme = true,
            getThemeColor = { ThemeManager.currentTheme.onSurface }
        )
        iconLabel.icon = loadVcsIcon()
        repaint()
    }

    /**
     * 更新 VCS Widget 的显示内容（显示 git 分支或"版本控制"）
     */
    fun updateDisplay() {
        val workspaceRoot = workspace.getWorkspaceRoot()

        textLabel.text = I18n.translate(I18nKeys.Status.VERSION_CONTROL)
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
                        textLabel.text = branchName
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
            // 尝试从主资源加载，使用主题自适应
            IconLoader.getIcon(
                IconRef("icons/common/git-branch.svg"),
                12,
                adaptToTheme = true,
                getThemeColor = { ThemeManager.currentTheme.onSurface }
            )
        } catch (e: Exception) {
            null
        }
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

            // 方法3：使用 git symbolic-ref --short HEAD（可处理 unborn branch，例如刚 git init 但尚未提交）
            try {
                val process3 = ProcessBuilder("git", "symbolic-ref", "--short", "HEAD")
                    .directory(workspaceRoot)
                    .redirectErrorStream(true)
                    .start()

                val output3 = process3.inputStream.bufferedReader().use { it.readText() }.trim()
                val exitCode3 = process3.waitFor()

                if (exitCode3 == 0 && output3.isNotBlank()) {
                    logger.debug("获取 git 分支成功 (symbolic-ref): {}", output3)
                    return output3
                }
            } catch (e: Exception) {
                logger.debug("git symbolic-ref 命令失败", e)
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
    private fun showVcsPopupMenu(invoker: Component) {
        val workspaceRoot = workspace.getWorkspaceRoot()
        val parent = SwingUtilities.getWindowAncestor(invoker) ?: SwingUtilities.getWindowAncestor(this) ?: this

        if (workspaceRoot == null || !workspaceRoot.exists()) {
            JOptionPane.showMessageDialog(
                parent,
                I18n.translate(I18nKeys.Dialog.WORKSPACE_NOT_OPENED),
                I18n.translate(I18nKeys.Dialog.TIP),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        if (!isGitAvailable()) {
            JOptionPane.showMessageDialog(
                parent,
                "未找到 git 命令，请确保 git 已安装并在 PATH 中",
                I18n.translate(I18nKeys.Dialog.ERROR),
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        if (!isGitRepository(workspaceRoot)) {
            promptCreateGitRepository(workspaceRoot, parent)
            return
        }

        val menu = JPopupMenu()

        menu.add(JMenuItem("更新项目").apply {
            icon = IconLoader.getIcon(
                IconRef("icons/vcs/update.svg"),
                16,
                adaptToTheme = true,
                getThemeColor = { ThemeManager.currentTheme.onSurface },
                getDisabledColor = { ThemeManager.currentTheme.onSurfaceVariant }
            )
            addActionListener { updateProject(workspaceRoot, parent) }
        })

        menu.add(JMenuItem("推送").apply {
            icon = IconLoader.getIcon(
                IconRef("icons/vcs/push.svg"),
                16,
                adaptToTheme = true,
                getThemeColor = { ThemeManager.currentTheme.onSurface },
                getDisabledColor = { ThemeManager.currentTheme.onSurfaceVariant }
            )
            addActionListener { pushProject(workspaceRoot, parent) }
        })

        menu.add(JMenuItem("新建分支…").apply {
            addActionListener { createBranch(workspaceRoot, parent) }
        })

        menu.addSeparator()

        val currentBranch = getCurrentGitBranch(workspaceRoot)
        val localBranches = listLocalBranches(workspaceRoot)
        menu.add(buildLocalBranchesMenu(workspaceRoot, parent, localBranches, currentBranch))

        val remoteBranches = listRemoteBranches(workspaceRoot)
        if (remoteBranches.isNotEmpty()) {
            menu.add(buildRemoteBranchesMenu(workspaceRoot, parent, remoteBranches, localBranches, currentBranch))
        }

        val tags = listTags(workspaceRoot)
        if (tags.isNotEmpty()) {
            menu.add(buildTagsMenu(workspaceRoot, parent, tags))
        }

        menu.show(invoker, 0, invoker.height)
    }

    private fun isGitRepository(workspaceRoot: File): Boolean {
        // .git 可能是目录或文件（worktree），只要存在即可视为仓库
        return File(workspaceRoot, ".git").exists()
    }

    private fun isGitAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("git", "--version").start()
            val finished = process.waitFor(3000, TimeUnit.MILLISECONDS)
            finished && process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun isCreateGitPromptSuppressed(): Boolean {
        return settings.get(SettingsStoreKeys.SUPPRESS_GIT_CREATE_PROMPT, "false") == "true"
    }

    private fun suppressCreateGitPrompt() {
        settings.put(SettingsStoreKeys.SUPPRESS_GIT_CREATE_PROMPT, "true")
        settings.sync()
    }

    private data class GitCommandResult(val exitCode: Int, val output: String)

    private fun runGitCommand(command: List<String>, workingDir: File, timeoutMs: Long): GitCommandResult {
        val pb = ProcessBuilder(command)
        pb.directory(workingDir)
        pb.redirectErrorStream(true)

        val process = try {
            pb.start()
        } catch (e: Exception) {
            return GitCommandResult(-1, "无法启动进程: ${e.message}")
        }

        val outputBuffer = StringBuilder()
        val outputReader = Thread {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        outputBuffer.append(line).append("\n")
                    }
                }
            } catch (_: Exception) {
                // 忽略读取错误
            }
        }.apply {
            isDaemon = true
            start()
        }

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
            outputReader.join(500)
            return GitCommandResult(-1, "命令执行超时（超过 ${timeoutMs}ms）")
        }

        outputReader.join(1000)
        val exitCode = process.exitValue()
        return GitCommandResult(exitCode, outputBuffer.toString().trim())
    }

    private fun promptCreateGitRepository(workspaceRoot: File, parent: Component) {
        if (isCreateGitPromptSuppressed()) return
        val options = arrayOf("创建", I18n.translate(I18nKeys.Action.CANCEL), "不再提示")
        val choice = JOptionPane.showOptionDialog(
            parent,
            "当前工作区不是 Git 仓库，是否创建一个新的 Git 仓库？",
            "创建 Git 仓库",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        )
        when (choice) {
            0 -> createGitRepository(workspaceRoot, parent)
            2 -> suppressCreateGitPrompt()
        }
    }

    private fun createGitRepository(workspaceRoot: File, parent: Component) {
        Thread {
            val result = runGitCommand(listOf("git", "init"), workspaceRoot, 10_000)
            SwingUtilities.invokeLater {
                if (result.exitCode == 0) {
                    JOptionPane.showMessageDialog(
                        parent,
                        "Git 仓库创建成功",
                        I18n.translate(I18nKeys.Dialog.INFO),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    updateDisplay()
                } else {
                    JOptionPane.showMessageDialog(
                        parent,
                        "Git 仓库创建失败：\n${result.output}",
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun updateProject(workspaceRoot: File, parent: Component) {
        Thread {
            val remotes = listRemotes(workspaceRoot)
            if (remotes.isEmpty()) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        parent,
                        "未配置远程仓库，无法更新项目",
                        I18n.translate(I18nKeys.Dialog.TIP),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
                return@Thread
            }

            val result = runGitCommand(listOf("git", "pull", "--ff-only"), workspaceRoot, 5 * 60_000L)
            SwingUtilities.invokeLater {
                if (result.exitCode == 0) {
                    JOptionPane.showMessageDialog(
                        parent,
                        if (result.output.isBlank()) "更新完成" else result.output,
                        I18n.translate(I18nKeys.Dialog.INFO),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    JOptionPane.showMessageDialog(
                        parent,
                        "更新失败：\n${result.output}",
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                updateDisplay()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun pushProject(workspaceRoot: File, parent: Component) {
        Thread {
            val remotes = listRemotes(workspaceRoot)
            if (remotes.isEmpty()) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        parent,
                        "未配置远程仓库，无法推送",
                        I18n.translate(I18nKeys.Dialog.TIP),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
                return@Thread
            }

            val result = runGitCommand(listOf("git", "push"), workspaceRoot, 5 * 60_000L)
            SwingUtilities.invokeLater {
                if (result.exitCode == 0) {
                    JOptionPane.showMessageDialog(
                        parent,
                        if (result.output.isBlank()) "推送完成" else result.output,
                        I18n.translate(I18nKeys.Dialog.INFO),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    JOptionPane.showMessageDialog(
                        parent,
                        "推送失败：\n${result.output}",
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                updateDisplay()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun createBranch(workspaceRoot: File, parent: Component) {
        val branchName = JOptionPane.showInputDialog(parent, "请输入新分支名称：", "新建分支", JOptionPane.PLAIN_MESSAGE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return

        Thread {
            val result = runGitCommand(listOf("git", "checkout", "-b", branchName), workspaceRoot, 10_000)
            SwingUtilities.invokeLater {
                if (result.exitCode == 0) {
                    JOptionPane.showMessageDialog(
                        parent,
                        "已创建并切换到分支：$branchName",
                        I18n.translate(I18nKeys.Dialog.INFO),
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    updateDisplay()
                } else {
                    JOptionPane.showMessageDialog(
                        parent,
                        "创建分支失败：\n${result.output}",
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun buildLocalBranchesMenu(
        workspaceRoot: File,
        parent: Component,
        branches: List<String>,
        currentBranch: String?
    ): JMenu {
        val menu = JMenu("本地分支")
        if (branches.isEmpty()) {
            menu.add(JMenuItem("暂无本地分支").apply { isEnabled = false })
            return menu
        }

        branches.forEach { branch ->
            val item = JMenuItem(branch).apply {
                if (branch == currentBranch) {
                    font = font.deriveFont(Font.BOLD)
                    isEnabled = false
                }
                addActionListener { checkoutLocalBranch(workspaceRoot, parent, branch) }
            }
            menu.add(item)
        }
        return menu
    }

    private fun buildRemoteBranchesMenu(
        workspaceRoot: File,
        parent: Component,
        remoteBranches: List<String>,
        localBranches: List<String>,
        currentBranch: String?
    ): JMenu {
        val menu = JMenu("远程分支")
        remoteBranches.forEach { remoteBranch ->
            val item = JMenuItem(remoteBranch).apply {
                addActionListener {
                    checkoutRemoteBranch(workspaceRoot, parent, remoteBranch, localBranches, currentBranch)
                }
            }
            menu.add(item)
        }
        return menu
    }

    private fun buildTagsMenu(workspaceRoot: File, parent: Component, tags: List<String>): JMenu {
        val menu = JMenu("标签")
        tags.forEach { tag ->
            menu.add(JMenuItem(tag).apply {
                addActionListener { checkoutTag(workspaceRoot, parent, tag) }
            })
        }
        return menu
    }

    private fun checkoutLocalBranch(workspaceRoot: File, parent: Component, branch: String) {
        Thread {
            val result = runGitCommand(listOf("git", "checkout", branch), workspaceRoot, 10_000)
            SwingUtilities.invokeLater {
                if (result.exitCode == 0) {
                    updateDisplay()
                } else {
                    JOptionPane.showMessageDialog(
                        parent,
                        "切换分支失败：\n${result.output}",
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun checkoutRemoteBranch(
        workspaceRoot: File,
        parent: Component,
        remoteBranch: String,
        localBranches: List<String>,
        currentBranch: String?
    ) {
        val candidateLocal = remoteBranch.substringAfter('/')
        val command = if (candidateLocal.isNotBlank() && localBranches.contains(candidateLocal)) {
            if (candidateLocal == currentBranch) return
            listOf("git", "checkout", candidateLocal)
        } else {
            listOf("git", "checkout", "--track", remoteBranch)
        }

        Thread {
            val result = runGitCommand(command, workspaceRoot, 20_000)
            SwingUtilities.invokeLater {
                if (result.exitCode == 0) {
                    updateDisplay()
                } else {
                    JOptionPane.showMessageDialog(
                        parent,
                        "切换远程分支失败：\n${result.output}",
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun checkoutTag(workspaceRoot: File, parent: Component, tag: String) {
        val choice = JOptionPane.showConfirmDialog(
            parent,
            "检出标签会进入 detached HEAD 状态，是否继续？",
            "检出标签",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        if (choice != JOptionPane.YES_OPTION) return

        Thread {
            val result = runGitCommand(listOf("git", "checkout", tag), workspaceRoot, 10_000)
            SwingUtilities.invokeLater {
                if (result.exitCode == 0) {
                    updateDisplay()
                } else {
                    JOptionPane.showMessageDialog(
                        parent,
                        "检出标签失败：\n${result.output}",
                        I18n.translate(I18nKeys.Dialog.ERROR),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun listRemotes(workspaceRoot: File): List<String> {
        val result = runGitCommand(listOf("git", "remote"), workspaceRoot, 3_000)
        if (result.exitCode != 0) return emptyList()
        return result.output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun listLocalBranches(workspaceRoot: File): List<String> {
        val result = runGitCommand(listOf("git", "branch"), workspaceRoot, 3_000)
        if (result.exitCode != 0) return emptyList()
        return result.output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                // * main /   dev
                line.removePrefix("*").trim()
            }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    private fun listRemoteBranches(workspaceRoot: File): List<String> {
        val result = runGitCommand(listOf("git", "branch", "-r"), workspaceRoot, 3_000)
        if (result.exitCode != 0) return emptyList()
        return result.output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.contains("->") } // origin/HEAD -> origin/main
            .distinct()
            .toList()
    }

    private fun listTags(workspaceRoot: File): List<String> {
        val result = runGitCommand(listOf("git", "tag", "--list"), workspaceRoot, 3_000)
        if (result.exitCode != 0) return emptyList()
        return result.output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }
}
