package editor.gui

import editor.gui.plugin.PluginManager
import editor.gui.ui.activitybar.ActivityBar
import editor.gui.ui.editor.Editor
import editor.gui.ui.panel.Panel
import editor.gui.ui.sidebar.SideBar
import editor.gui.ui.statusbar.StatusBar
import editor.gui.ui.titlebar.TitleBar
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JSplitPane
import javax.swing.UIManager

/**
 * APK编辑器主窗口
 */
object MainFrame : JFrame() {
    val instance: MainFrame get() = this

    // UI组件实例
    val titleBar = TitleBar(this)
    val activityBar = ActivityBar(this)
    val sideBar = SideBar(this)
    val editor = Editor(this)
    val panel = Panel(this)
    val statusBar = StatusBar(this)

    private val horizontalSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sideBar, editor).apply {
        dividerLocation = 250
        isOneTouchExpandable = false
        dividerSize = 8
    }

    private val verticalSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, horizontalSplit, panel).apply {
        dividerLocation = 700
        isOneTouchExpandable = false
        dividerSize = 8
    }

    init {
        initializeWindow()
        setupLayout()
        connectComponents()
        loadPlugins()
    }

    private fun initializeWindow() {
        title = "APK Editor"
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        size = Dimension(1400, 900)
        setLocationRelativeTo(null)
        minimumSize = Dimension(800, 600)

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
            statusBar.setMessage("警告: 无法设置系统外观")
        }
    }

    private fun setupLayout() {
        layout = BorderLayout()
        add(titleBar, BorderLayout.NORTH)
        add(activityBar, BorderLayout.WEST)
        add(verticalSplit, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun connectComponents() {
        activityBar.sideBar = sideBar
        activityBar.panel = panel
    }

    private fun loadPlugins() {
        try {
            PluginManager.loadPlugins()
            statusBar.setMessage("插件系统已启动")
        } catch (e: Exception) {
            statusBar.setMessage("插件加载失败: ${e.message}")
        }
    }
}

