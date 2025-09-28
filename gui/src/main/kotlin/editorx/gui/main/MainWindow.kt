package editorx.gui.main

import editorx.gui.CachedViewProvider
import editorx.gui.GuiControl
import editorx.gui.core.Constants
import editorx.widget.NoLineSplitPaneUI
import editorx.gui.main.activitybar.ActivityBar
import editorx.gui.main.editor.Editor
import editorx.gui.main.explorer.Explorer
import editorx.gui.main.sidebar.SideBar
import editorx.gui.main.statusbar.StatusBar
import editorx.gui.main.menubar.MenuBar
import editorx.plugin.PluginManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JSplitPane

class MainWindow(val guiControl: GuiControl) : JFrame() {

    // UI 组件
    val titleBar by lazy { MenuBar(this) }
    val activityBar by lazy { ActivityBar(this) }
    val sideBar by lazy { SideBar(this) }
    val editor by lazy { Editor(this) }
    val statusBar by lazy { StatusBar(this) }

    var pluginManager: PluginManager? = null

    private val horizontalSplit by lazy {
        JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sideBar, editor).apply {
            dividerLocation = 0  // 初始时隐藏SideBar
            isOneTouchExpandable = false
            dividerSize = 8
        }
    }

    init {
        setupWindow()
        setupLayout()
        tuneSplitPanes()
        setupActivityBarDefaultItems()
    }

    private fun setupWindow() {
        // 窗口属性
        title = "EditorX"
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        size = Dimension(1400, 900)
        setLocationRelativeTo(null)
        minimumSize = Dimension(800, 600)
    }

    private fun setupLayout() {
        // 布局
        layout = BorderLayout()
        // 正确安装菜单栏，避免某些 LAF 下作为普通组件加入导致不显示
        jMenuBar = titleBar
        add(activityBar, BorderLayout.WEST)
        // 暂时直接使用水平分割面板，不包含Panel
        add(horizontalSplit, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun tuneSplitPanes() {
        horizontalSplit.setUI(NoLineSplitPaneUI())
        horizontalSplit.border = javax.swing.BorderFactory.createEmptyBorder()
        // 启用连续布局，减少布局跳动
        horizontalSplit.isContinuousLayout = true

        // 当用户手动拖动分割条把 SideBar 拉出时，若尚未有激活视图，则激活默认项
        horizontalSplit.addPropertyChangeListener(javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY) { _ ->
            val visible = horizontalSplit.dividerLocation > 0
            if (visible && sideBar.getCurrentViewId() == null) {
                sideBar.preserveNextDividerOnShow()
                activityBar.activateItem(Constants.ACTIVITY_BAR_DEFAULT_ID, userInitiated = false)
            } else if (visible && sideBar.getCurrentViewId() != null) {
                // 用户手动拖拽显示：仅同步按钮高亮，不再触发切换逻辑，避免误判为已显示而反向关闭
                activityBar.highlightOnly(sideBar.getCurrentViewId()!!)
            } else if (!visible) {
                activityBar.clearActive()
                // 同步SideBar内部状态为隐藏，避免点击按钮时被误判为已可见
                sideBar.hideSideBar()
            }
        }
    }

    private fun setupActivityBarDefaultItems() {
        // 添加默认的Explorer
        activityBar.addItem(
            "explorer",
            "Explorer",
            "icons/folder.svg",
            object : CachedViewProvider() {
                override fun createView() = Explorer(this@MainWindow)
            }
        )
    }

    fun openFileChooserAndOpen() {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.FILES_ONLY; dialogTitle = "选择文件" }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file: File = chooser.selectedFile
            editor.openFile(file)
            guiControl.workspace.addRecentFile(file)
        }
    }
}
