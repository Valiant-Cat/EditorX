package editorx.gui.main.titlebar

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import editorx.core.workspace.Workspace
import editorx.gui.ThemeManager
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*

/**
 * VCS Widget - 显示版本控制信息（如 git 分支）
 * 参考 IDEA/Android Studio 的效果
 */
class VcsWidget(private val workspace: Workspace) : JPanel() {
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
                IconRef("icons/gui/git-branch.svg"), 
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
        // TODO
    }
}

