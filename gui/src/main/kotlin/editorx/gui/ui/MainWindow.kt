package editorx.gui.ui

import editorx.gui.ui.activitybar.ActivityBar
import editorx.gui.ui.editor.Editor
import editorx.gui.ui.panel.Panel
import editorx.gui.ui.sidebar.SideBar
import editorx.gui.ui.statusbar.StatusBar
import editorx.gui.ui.titlebar.TitleBar
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JSplitPane

/**
 * EditorX 主窗口
 * - 解耦 UI 构建与插件加载
 * - 提供显式的 showWindow()/loadPluginsSafely()
 */
class MainWindow : JFrame() {

    // UI 组件
    val titleBar by lazy { TitleBar(this) }
    val activityBar by lazy { ActivityBar(this) }
    val sideBar by lazy { SideBar(this) }
    val editor by lazy { Editor(this) }
    val panel by lazy { Panel(this) }
    val statusBar by lazy { StatusBar(this) }

    private val horizontalSplit by lazy {
        JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sideBar, editor).apply {
            dividerLocation = 0  // 初始时隐藏SideBar
            isOneTouchExpandable = false
            dividerSize = 8
        }
    }

    private val verticalSplit by lazy {
        JSplitPane(JSplitPane.VERTICAL_SPLIT, horizontalSplit, panel).apply {
            dividerLocation = Int.MAX_VALUE  // 初始时隐藏Panel
            isOneTouchExpandable = false
            dividerSize = 8
        }
    }

    init {
        setupWindow()
        setupLayout()
        connectComponents()
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
        add(titleBar, BorderLayout.NORTH)
        add(activityBar, BorderLayout.WEST)
        add(verticalSplit, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun connectComponents() {
        // 组件连接
        activityBar.sideBar = sideBar
        activityBar.panel = panel
    }
}
