package editorx.gui

import editorx.core.gui.GuiContext
import editorx.core.util.SystemUtils
import editorx.gui.widget.NoLineSplitPaneUI
import editorx.gui.workbench.activitybar.ActivityBar
import editorx.gui.workbench.agent.AgentPanel
import editorx.gui.workbench.editor.Editor
import editorx.gui.workbench.explorer.Explorer
import editorx.gui.workbench.menubar.MenuBar
import editorx.gui.workbench.navigationbar.NavigationBar
import editorx.gui.workbench.sidebar.RightSideBar
import editorx.gui.workbench.sidebar.SideBar
import editorx.gui.workbench.statusbar.StatusBar
import editorx.gui.workbench.titlebar.TitleBar
import editorx.gui.workbench.toolbar.ToolBar
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionAdapter
import java.io.File
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JSplitPane

class MainWindow(val guiContext: GuiContext) : JFrame() {

    // UI 组件
    private val _menuBar by lazy { MenuBar(this) }
    val titleBar by lazy { TitleBar(this) }
    val toolBar by lazy { ToolBar(this) }
    val activityBar by lazy { ActivityBar(this) }
    val sideBar by lazy { SideBar(this) }
    val rightSideBar by lazy { RightSideBar(this) }
    val editor by lazy { Editor(this) }
    val statusBar by lazy { StatusBar(this) }
    val navigationBar by lazy { NavigationBar(this) }

    // Editor 与右侧 SideBar 的水平分割
    private val rightSplit by lazy {
        JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, rightSideBar).apply {
            dividerLocation = Int.MAX_VALUE  // 初始时右侧 SideBar 隐藏
            isOneTouchExpandable = false
            dividerSize = 0  // 初始时右侧 SideBar 关闭，隐藏拖拽条
        }
    }

    // SideBar 与 Editor+右侧 SideBar 的水平分割（ActivityBar 独立在外层）
    private val leftSplit by lazy {
        JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sideBar, rightSplit).apply {
            dividerLocation = 0  // 初始时 SideBar 隐藏
            isOneTouchExpandable = false
            dividerSize = 0  // 初始时 SideBar 关闭，隐藏拖拽条
        }
    }

    // 主容器：ActivityBar（固定） + leftSplit（SideBar + Editor + 右侧 SideBar）
    private val mainContentContainer by lazy {
        JPanel(BorderLayout()).apply {
            isOpaque = false
            // ActivityBar 固定在左侧，宽度固定为 38，不受 SideBar 拖动影响
            add(activityBar, BorderLayout.WEST)
            // leftSplit 在右侧，包含 SideBar 与 Editor+右侧 SideBar
            add(leftSplit, BorderLayout.CENTER)
        }
    }

    init {
        initWindow()
        setupLayout()
    }

    private fun initWindow() {
        // 窗口属性
        title = "EditorX"
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(1280, 800)
        setLocationRelativeTo(null)
        minimumSize = Dimension(800, 600)

        if (SystemUtils.isMacOS()) {
            // 启用 macOS 外观
            System.setProperty("apple.laf.useScreenMenuBar", "true")
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "EditorX")

            // 保留原生窗口装饰，但启用全窗口内容模式
            rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        }
    }

    private fun setupLayout() {
        // 布局
        layout = BorderLayout()
        // 正确安装菜单栏，避免某些 LAF 下作为普通组件加入导致不显示
        jMenuBar = _menuBar

        // 将 titleBar 和 toolBar 放在一个垂直容器中
        val topContainer = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(titleBar, BorderLayout.NORTH)
            add(toolBar, BorderLayout.SOUTH)
        }
        add(topContainer, BorderLayout.NORTH)

        // 将 navigationBar 和 mainContentContainer 放在一个容器中
        val centerContainer = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(navigationBar, BorderLayout.NORTH)
            add(mainContentContainer, BorderLayout.CENTER)
        }
        add(centerContainer, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)

        leftSplit.setUI(NoLineSplitPaneUI())
        leftSplit.border = javax.swing.BorderFactory.createEmptyBorder()
        rightSplit.setUI(NoLineSplitPaneUI())
        rightSplit.border = javax.swing.BorderFactory.createEmptyBorder()
        // 启用连续布局，减少布局跳动
        leftSplit.isContinuousLayout = true
        rightSplit.isContinuousLayout = true

        rightSideBar.registerView("agent", AgentPanel(this))

        // 当用户手动拖动分割条时，同步左侧 SideBar 状态
        leftSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) { _ ->
            val sidebarVisible = leftSplit.dividerLocation > 0
            syncLeftVisibilityWithDivider()
            titleBar.updateSideBarIcon(sidebarVisible)
        }

        // 当用户手动拖动分割条时，同步右侧 SideBar 状态
        rightSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) { _ ->
            val sidebarVisible = rightSplit.dividerLocation < rightSplit.maximumDividerLocation
            syncRightVisibilityWithDivider()
            titleBar.updateAgentPanelIcon(sidebarVisible)
        }

        // 初始化时同步 ActivityBar 的状态（确保高亮状态与实际显示状态一致）
        // 由于 SideBar 初始是隐藏的，所以不应该有任何按钮高亮
        syncLeftVisibilityWithDivider()
        syncRightVisibilityWithDivider()

        // 添加自定义拖拽功能：在 SideBar 右边缘和右侧 SideBar 左边缘检测拖拽
        addDragListeners()
    }

    /**
     * 同步 SideBar 内部状态与分割条位置
     * 当用户手动拖拽分割条时调用此方法来同步内部状态
     */
    private fun syncLeftVisibilityWithDivider() {
        val dividerLocation = leftSplit.dividerLocation
        // SideBar 可见的条件是 dividerLocation > 0
        val shouldBeVisible = dividerLocation > 0

        if (shouldBeVisible != sideBar.isSideBarVisible()) {
            sideBar.syncVisibilityFromDivider(shouldBeVisible)
            // 同步 ActivityBar 的状态
            if (shouldBeVisible) {
                // 如果 SideBar 变为可见，同步 ActivityBar 的高亮状态
                val currentViewId = sideBar.getCurrentViewId()
                if (currentViewId != null) {
                    activityBar.highlightOnly(currentViewId)
                }
            } else {
                // 如果 SideBar 变为隐藏，清除 ActivityBar 的激活状态
                activityBar.clearActive()
            }
        } else {
            // 即使可见性没有变化，也要同步高亮状态（确保一致性）
            if (shouldBeVisible) {
                val currentViewId = sideBar.getCurrentViewId()
                if (currentViewId != null) {
                    activityBar.highlightOnly(currentViewId)
                } else {
                    activityBar.clearActive()
                }
            } else {
                activityBar.clearActive()
            }
        }
    }

    /**
     * 同步右侧 SideBar 的显示状态
     */
    private fun syncRightVisibilityWithDivider() {
        val shouldBeVisible = rightSplit.dividerLocation < rightSplit.maximumDividerLocation
        if (shouldBeVisible != rightSideBar.isSideBarVisible()) {
            rightSideBar.syncVisibilityFromDivider(shouldBeVisible)
        }
    }

    private var isLeftDragging = false
    private var leftDragStartX = 0
    private var leftDragStartLocation = 0
    private var isRightDragging = false
    private var rightDragStartX = 0
    private var rightDragStartLocation = 0

    private fun addDragListeners() {
        // 在 SideBar 右边缘添加鼠标监听器
        sideBar.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                val rightEdge = sideBar.width
                val mouseX = e.x
                // 检测是否在右边缘 5 像素范围内
                if (mouseX >= rightEdge - 5 && mouseX <= rightEdge && leftSplit.dividerLocation > 0) {
                    isLeftDragging = true
                    leftDragStartX = e.xOnScreen
                    leftDragStartLocation = leftSplit.dividerLocation
                    sideBar.cursor = java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)
                }
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (isLeftDragging) {
                    isLeftDragging = false
                    sideBar.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                }
            }
        })

        sideBar.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                if (isLeftDragging) {
                    val deltaX = e.xOnScreen - leftDragStartX
                    val newLocation = (leftDragStartLocation + deltaX).coerceAtLeast(0)
                        .coerceAtMost(leftSplit.width)
                    leftSplit.dividerLocation = newLocation
                }
            }

            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                val rightEdge = sideBar.width
                val mouseX = e.x
                if (mouseX >= rightEdge - 5 && mouseX <= rightEdge && leftSplit.dividerLocation > 0) {
                    sideBar.cursor = java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)
                } else {
                    sideBar.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                }
            }
        })

        // 在右侧 SideBar 左边缘添加鼠标监听器
        rightSideBar.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                val leftEdge = 0
                val mouseX = e.x
                if (mouseX >= leftEdge && mouseX <= leftEdge + 5 &&
                    rightSplit.dividerLocation < rightSplit.maximumDividerLocation
                ) {
                    isRightDragging = true
                    rightDragStartX = e.xOnScreen
                    rightDragStartLocation = rightSplit.dividerLocation
                    rightSideBar.cursor = java.awt.Cursor(java.awt.Cursor.W_RESIZE_CURSOR)
                }
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (isRightDragging) {
                    isRightDragging = false
                    rightSideBar.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                }
            }
        })

        rightSideBar.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                if (isRightDragging) {
                    val deltaX = e.xOnScreen - rightDragStartX
                    val newLocation = (rightDragStartLocation + deltaX)
                        .coerceAtLeast(0)
                        .coerceAtMost(rightSplit.maximumDividerLocation)
                    rightSplit.dividerLocation = newLocation
                }
            }

            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                val mouseX = e.x
                if (mouseX <= 5 && rightSplit.dividerLocation < rightSplit.maximumDividerLocation) {
                    rightSideBar.cursor = java.awt.Cursor(java.awt.Cursor.W_RESIZE_CURSOR)
                } else {
                    rightSideBar.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                }
            }
        })
    }

    /**
     * 更新面包屑导航栏
     */
    fun updateNavigation(currentFile: File?) {
        navigationBar.update(currentFile)
    }

    /**
     * 统一处理打开文件的逻辑
     * 所有打开文件的操作都应该调用此方法
     */
    fun openFile(file: File) {
        if (!file.isFile || !file.canRead()) {
            return
        }
        editor.openFile(file)
        guiContext.getWorkspace().addRecentFile(file)
        editor.showEditorContent()
    }

    /**
     * 统一处理打开工作区的逻辑
     * 所有打开目录/工作区的操作都应该调用此方法
     */
    fun openWorkspace(workspace: File) {
        if (!workspace.isDirectory) {
            return
        }
        guiContext.getWorkspace().openWorkspace(workspace)
        guiContext.getWorkspace().addRecentWorkspace(workspace)
        // 通过 ActivityBar 显示 Explorer（会自动更新高亮状态）
        activityBar.activateItem("explorer", userInitiated = false)
        (sideBar.getView("explorer") as? Explorer)?.refreshRoot()
        titleBar.updateVcsDisplay()
        editor.showEditorContent()
        // 检查并提示创建 Git 仓库
        (sideBar.getView("explorer") as? Explorer)?.checkAndPromptCreateGitRepository(workspace)
    }

    /**
     * 显示文件选择对话框并打开选中的文件
     * 返回选中的文件，如果用户取消则返回 null
     */
    fun showFileChooser(callback: (File?) -> Unit) {
        val fileDialog = java.awt.FileDialog(this, "选择文件", java.awt.FileDialog.LOAD).apply {
            isMultipleMode = false
        }
        fileDialog.isVisible = true

        val fileName = fileDialog.file
        val dir = fileDialog.directory

        if (fileName != null && dir != null) {
            val selectedFile = File(dir, fileName)
            callback.invoke(selectedFile)
        } else {
            callback.invoke(null)
        }
    }

    /**
     * 显示文件夹选择对话框并打开选中的工作区
     * 返回选中的文件夹，如果用户取消则返回 null
     */
    fun showFolderChooser(callback: (File?) -> Unit) {
        val fileDialog = java.awt.FileDialog(this, "选择项目文件夹", java.awt.FileDialog.LOAD).apply {
            isMultipleMode = false
            // 在 macOS 上，设置系统属性以使用文件夹选择模式
            if (System.getProperty("os.name").lowercase().contains("mac")) {
                System.setProperty("apple.awt.fileDialogForDirectories", "true")
            }
        }
        fileDialog.isVisible = true

        val selectedDir = fileDialog.directory?.let { dir ->
            val fileName = fileDialog.file
            if (fileName != null) {
                File(dir, fileName)
            } else {
                File(dir)
            }
        }

        // 恢复系统属性
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }

        callback.invoke(selectedDir)
    }
}
