package editorx.gui.ui

import editorx.gui.services.GuiServices
import editorx.gui.ui.activitybar.ActivityBar
import editorx.gui.ui.editor.Editor
import editorx.gui.ui.panel.Panel
import editorx.gui.ui.sidebar.SideBar
import editorx.gui.ui.statusbar.StatusBar
import editorx.gui.ui.titlebar.TitleBar
import editorx.plugin.PluginManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JSplitPane

/**
 * EditorX 主窗口
 * - 解耦 UI 构建与插件加载
 * - 提供显式的 showWindow()/loadPluginsSafely()
 */
class MainWindow(val services: GuiServices) : JFrame() {

    // UI 组件
    val titleBar by lazy { TitleBar(this) }
    val activityBar by lazy { ActivityBar(this) }
    val sideBar by lazy { SideBar(this) }
    val editor by lazy { Editor(this) }
    val panel by lazy { Panel(this) }
    val statusBar by lazy { StatusBar(this) }

    // Plugin Manager reference for UI integrations
    var pluginManager: PluginManager? = null

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
        tuneSplitPanes()
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
        add(verticalSplit, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun connectComponents() {
        // 组件连接
        activityBar.sideBar = sideBar
        activityBar.panel = panel
    }

    private fun tuneSplitPanes() {
        // 使用不绘制边线的分割条 UI
        val customUI = editorx.gui.ui.util.NoLineSplitPaneUI()
        horizontalSplit.setUI(customUI)
        verticalSplit.setUI(editorx.gui.ui.util.NoLineSplitPaneUI())
        horizontalSplit.border = javax.swing.BorderFactory.createEmptyBorder()
        verticalSplit.border = javax.swing.BorderFactory.createEmptyBorder()
        // 启用连续布局，减少布局跳动
        horizontalSplit.isContinuousLayout = true
        verticalSplit.isContinuousLayout = true
        // 始终保留一个可拖拽的分割条（8px），隐藏时配合 Panel 逻辑将其贴底
        verticalSplit.dividerSize = 8
    }

    fun openFileChooserAndOpen() {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.FILES_ONLY; dialogTitle = "选择文件" }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file: File = chooser.selectedFile
            editor.openFile(file)
            services.workspace.addRecentFile(file)
        }
    }
}
