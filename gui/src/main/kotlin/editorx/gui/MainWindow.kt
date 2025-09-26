package editorx.gui

import editorx.gui.ui.widget.NoLineSplitPaneUI
import editorx.gui.workbench.activitybar.ActivityBar
import editorx.gui.workbench.editor.Editor
import editorx.gui.workbench.explorer.Explorer
import editorx.gui.workbench.sidebar.SideBar
import editorx.gui.workbench.statusbar.StatusBar
import editorx.gui.workbench.titlebar.TitleBar
import editorx.plugin.PluginManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JSplitPane

class MainWindow(val guiControl: GuiControl) : JFrame() {

    // UI 组件
    val titleBar by lazy { TitleBar(this) }
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
    }

    private fun setupActivityBarDefaultItems() {
        // 添加默认的Explorer
        activityBar.addItem(
            "explorer",
            "Explorer",
            "icons/explorer.svg",
            object : CachedViewProvider() {
                override fun createView() = Explorer()
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
