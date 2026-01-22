package editorx.gui.workbench.titlebar

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.service.BuildStatus
import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import editorx.core.util.SystemUtils
import editorx.gui.Constants
import editorx.gui.MainWindow
import editorx.gui.search.SearchDialog
import editorx.gui.settings.SettingsDialog
import editorx.gui.theme.ThemeManager
import editorx.gui.workbench.explorer.Explorer
import java.io.File
import java.awt.Font
import java.awt.Insets
import java.awt.event.ActionListener
import javax.swing.*

/**
 * 顶部工具栏：常用操作的快捷入口。
 * 在 macOS 上菜单集成到系统顶栏时，窗口内仍保留此工具栏以便操作。
 */
class TitleBar(private val mainWindow: MainWindow) : JToolBar() {
    companion object {
        private const val ICON_SIZE = 18
    }

    private var toggleSideBarButton: JButton? = null
    private var toggleAgentPanelButton: JButton? = null
    private var buildButton: JButton? = null
    private var buildGroup: JComponent? = null
    private var searchButton: JButton? = null
    private var settingsButton: JButton? = null
    private var compileTask: Thread? = null
    private var titleLabel: JLabel? = null

    // VCS Widget
    private val vcsWidget = VcsWidget(
        mainWindow.guiContext.getWorkspace(),
        mainWindow.guiContext.getSettings()
    )

    init {
        isFloatable = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = true
        updateTheme()
        buildActions()
        updateBuildActionVisibility()

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
        toggleAgentPanelButton?.icon = getAgentPanelIcon()

        revalidate()
        repaint()
    }


    /**
     * 更新 VCS Widget 的显示内容
     */
    fun updateVcsDisplay() {
        vcsWidget.updateDisplay()
        // 同时更新标题（工作区可能已变化）
        updateTitle()
        updateBuildActionVisibility()
    }

    /**
     * 更新标题栏显示的项目名称和未保存文件提示
     * 参考 jadx：如果有未保存的文件，在标题前显示 *
     */
    fun updateTitle() {
        val workspaceRoot = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()
        val hasUnsavedChanges = mainWindow.editor.hasUnsavedChanges()

        val title = when {
            workspaceRoot != null -> {
                val projectName = workspaceRoot.name
                if (hasUnsavedChanges) "* $projectName" else projectName
            }

            else -> "EditorX"
        }

        titleLabel?.text = title
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
        // 初始化标题
        updateTitle()
    }

    private fun setupRightActions() {
        buildGroup = Box.createHorizontalBox().apply {
            isOpaque = false
        }

        buildButton = JButton(
            IconLoader.getIcon(
                IconRef("icons/common/build.svg"),
                ICON_SIZE,
                adaptToTheme = true,
                getThemeColor = { ThemeManager.currentTheme.onSurface }
            )
        ).compact(I18n.translate(I18nKeys.Toolbar.BUILD)) {
            buildWorkspace()
        }

        buildGroup?.apply {
            add(buildButton)
            add(Box.createHorizontalStrut(12))
            add(JToolBar.Separator())
            add(Box.createHorizontalStrut(12))
        }
        add(buildGroup)

        toggleSideBarButton = JButton(getSideBarIcon()).compact(I18n.translate(I18nKeys.Toolbar.TOGGLE_SIDEBAR)) {
            toggleSideBar()
        }
        add(toggleSideBarButton)

        add(Box.createHorizontalStrut(6))

        toggleAgentPanelButton = JButton(getAgentPanelIcon()).compact(I18n.translate(I18nKeys.Toolbar.TOGGLE_AGENT_PANEL)) {
            toggleAgentPanel()
        }
        add(toggleAgentPanelButton)

        add(Box.createHorizontalStrut(6))

        // 全局搜索按钮（Command+K）
        val globalSearchShortcutText = if (SystemUtils.isMacOS()) "⌘K" else "Ctrl+K"
        searchButton = JButton(
            IconLoader.getIcon(
                IconRef("icons/common/search.svg"),
                ICON_SIZE,
                adaptToTheme = true,
                getThemeColor = { ThemeManager.currentTheme.onSurface }
            )
        ).compactWithShortcut(I18n.translate(I18nKeys.Toolbar.GLOBAL_SEARCH), globalSearchShortcutText) {
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

    private fun updateBuildActionVisibility() {
        val workspaceRoot = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()
        val hasBuildProvider = workspaceRoot != null &&
            mainWindow.guiContext.getPluginManager().findBuildProvider(workspaceRoot) != null
        buildGroup?.isVisible = hasBuildProvider
        revalidate()
        repaint()
    }

    fun updateSideBarIcon(sideBarVisible: Boolean) {
        toggleSideBarButton?.icon = getSideBarIcon()
    }

    fun updateAgentPanelIcon(panelVisible: Boolean) {
        toggleAgentPanelButton?.icon = getAgentPanelIcon()
    }

    private fun getSideBarIcon(): Icon? {
        val isVisible = mainWindow.sideBar.isSideBarVisible()
        val iconName =
            if (isVisible) "icons/common/layout-sidebar-left.svg" else "icons/common/layout-sidebar-left-off.svg"
        return IconLoader.getIcon(
            IconRef(iconName),
            ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { ThemeManager.currentTheme.onSurface }
        )
    }

    private fun getAgentPanelIcon(): Icon? {
        val isVisible = mainWindow.rightSideBar.isSideBarVisible()
        val iconName =
            if (isVisible) "icons/common/layout-sidebar-right.svg" else "icons/common/layout-sidebar-right-off.svg"
        return IconLoader.getIcon(
            IconRef(iconName),
            ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { ThemeManager.currentTheme.onSurface }
        )
    }

    private fun toggleSideBar() {
        val sidebar = mainWindow.sideBar
        if (sidebar.isSideBarVisible()) {
            sidebar.hideSideBar()
        } else {
            val targetId = sidebar.getCurrentViewId()
                ?: Constants.ACTIVITY_BAR_DEFAULT_ID.takeIf { it.isNotBlank() }
                ?: sidebar.getRegisteredViewIds().firstOrNull()

            if (targetId != null) {
                when {
                    sidebar.hasView(targetId) -> sidebar.showView(targetId)
                    else -> {
                        mainWindow.activityBar.activateItem(targetId, userInitiated = true)
                        if (!sidebar.isSideBarVisible()) {
                            sidebar.getRegisteredViewIds().firstOrNull()?.let { sidebar.showView(it) }
                        }
                    }
                }
            }
        }
        toggleSideBarButton?.icon = getSideBarIcon()
    }

    private fun toggleAgentPanel() {
        val panel = mainWindow.rightSideBar
        if (panel.isSideBarVisible()) {
            panel.hideSideBar()
        } else {
            val targetId = panel.getCurrentViewId() ?: panel.getRegisteredViewIds().firstOrNull()
            if (targetId != null) {
                if (panel.hasView(targetId)) {
                    panel.showView(targetId)
                } else {
                    panel.getRegisteredViewIds().firstOrNull()?.let { panel.showView(it) }
                }
            }
        }
        toggleAgentPanelButton?.icon = getAgentPanelIcon()
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


    private fun buildWorkspace() {
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
                I18n.translate(I18nKeys.Dialog.WORKSPACE_NOT_OPENED),
                I18n.translate(I18nKeys.Dialog.TIP),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        // 查找可以提供构建能力的插件
        val buildProvider = mainWindow.guiContext.getPluginManager().findBuildProvider(workspaceRoot)
        if (buildProvider == null) {
            JOptionPane.showMessageDialog(
                mainWindow,
                I18n.translate(I18nKeys.ToolbarMessage.NO_BUILD_PROVIDER),
                I18n.translate(I18nKeys.Dialog.TIP),
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val progressHandle =
            mainWindow.statusBar.beginProgressTask(I18n.translate(I18nKeys.ToolbarMessage.COMPILING_APK), indeterminate = true)
        compileTask = Thread {
            try {
                val buildResult = buildProvider.build(workspaceRoot) { progressMessage ->
                    SwingUtilities.invokeLater {
                        mainWindow.statusBar.updateProgressTask(progressHandle, progressMessage, indeterminate = true)
                    }
                }

                SwingUtilities.invokeLater {
                    mainWindow.statusBar.endProgressTask(progressHandle)
                    when (buildResult.status) {
                        BuildStatus.SUCCESS -> {
                            val outputFile = buildResult.outputFile
                            if (outputFile != null) {
                                (mainWindow.sideBar.getView("explorer") as? Explorer)?.refreshRootPreserveSelection()
                                mainWindow.statusBar.showSuccess(
                                    I18n.translate(I18nKeys.ToolbarMessage.COMPILE_SUCCESS)
                                        .format(outputFile.name)
                                )
                                showBuildCompleteDialog(outputFile)
                            } else {
                                mainWindow.statusBar.showSuccess(
                                    I18n.translate(I18nKeys.ToolbarMessage.COMPILE_SUCCESS).format("")
                                )
                            }
                        }

                        BuildStatus.NOT_FOUND -> {
                            val msg =
                                buildResult.errorMessage ?: I18n.translate(I18nKeys.ToolbarMessage.BUILD_TOOL_NOT_FOUND)
                            mainWindow.statusBar.showError(msg)
                            JOptionPane.showMessageDialog(
                                mainWindow,
                                msg,
                                I18n.translate(I18nKeys.Dialog.ERROR),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }

                        BuildStatus.CANCELLED -> {
                            mainWindow.statusBar.setMessage(I18n.translate(I18nKeys.ToolbarMessage.COMPILE_CANCELLED))
                        }

                        BuildStatus.FAILED -> {
                            val msg = buildResult.errorMessage ?: I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED)
                                .format("")
                            mainWindow.statusBar.showError(msg)
                            JOptionPane.showMessageDialog(
                                mainWindow,
                                buildResult.output ?: msg,
                                I18n.translate(I18nKeys.Dialog.ERROR),
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    mainWindow.statusBar.endProgressTask(progressHandle)
                    mainWindow.statusBar.showError(
                        "${I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED).split("(")[0].trim()}: ${e.message}"
                    )
                    JOptionPane.showMessageDialog(
                        mainWindow,
                        I18n.translate(I18nKeys.ToolbarMessage.COMPILE_EXCEPTION).format(e.message ?: ""),
                        I18n.translate(I18nKeys.Dialog.ERROR),
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

    private fun showBuildCompleteDialog(outputFile: File) {
        val result = JOptionPane.showOptionDialog(
            mainWindow,
            I18n.translate(I18nKeys.ToolbarMessage.BUILD_GENERATED).format(outputFile.absolutePath),
            I18n.translate(I18nKeys.ToolbarMessage.COMPILE_COMPLETE),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            arrayOf("定位", "关闭"),
            "关闭"
        )
        if (result == 0) {
            revealInExplorer(outputFile)
        }
    }

    private fun revealInExplorer(file: File) {
        mainWindow.activityBar.activateItem("explorer")
        val explorer = mainWindow.sideBar.getView("explorer") as? Explorer ?: return

        // 触发一次刷新，避免目录已加载但未 reload 导致找不到新文件。
        explorer.refreshRootPreserveSelection()

        var attempts = 0
        val timer = Timer(150, null)
        timer.addActionListener {
            attempts++
            explorer.focusFileInTree(file)
            if (attempts >= 10) timer.stop()
        }
        timer.initialDelay = 0
        timer.start()
    }

}
