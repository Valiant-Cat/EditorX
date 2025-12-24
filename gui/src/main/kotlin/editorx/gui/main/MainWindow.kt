package editorx.gui.main

import editorx.core.gui.CachedGuiViewProvider
import editorx.gui.GuiEnvironment
import editorx.gui.core.Constants
import editorx.gui.main.activitybar.ActivityBar
import editorx.gui.main.editor.Editor
import editorx.gui.main.explorer.Explorer
import editorx.gui.main.menubar.MenuBar
import editorx.gui.main.search.SearchView
import editorx.gui.main.sidebar.SideBar
import editorx.gui.main.statusbar.StatusBar
import editorx.gui.main.toolbar.ToolBar
import editorx.core.plugin.PluginManager
import editorx.core.plugin.PluginState
import editorx.gui.util.NoLineSplitPaneUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JSplitPane

class MainWindow(val guiControl: GuiEnvironment) : JFrame() {

    // UI 组件
    val titleBar by lazy { MenuBar(this) }
    val toolBar by lazy { ToolBar(this) }
    val activityBar by lazy { ActivityBar(this) }
    val sideBar by lazy { SideBar(this) }
    val editor by lazy { Editor(this) }
    val statusBar by lazy { StatusBar(this) }

    private val pluginListener = object : PluginManager.Listener {
        override fun onPluginChanged(pluginId: String) {
            val pm = pluginManager ?: return
            val state = pm.getPlugin(pluginId)?.state
            if (state != PluginState.STARTED) {
                // 插件被停用/失败：移除可能残留的入口与视图
                sideBar.removeView(pluginId)
                activityBar.removeViewProvider(pluginId)
            }
            editor.refreshSyntaxForOpenTabs()
        }

        override fun onPluginUnloaded(pluginId: String) {
            // 插件卸载：无条件移除入口与视图
            sideBar.removeView(pluginId)
            activityBar.removeViewProvider(pluginId)
            editor.refreshSyntaxForOpenTabs()
        }
    }

    var pluginManager: PluginManager? = null
        set(value) {
            field?.removeListener(pluginListener)
            field = value
            value?.addListener(pluginListener)
        }

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
        setupDefaultActivityBarItems()
    }

    private fun setupWindow() {
        // 窗口属性
        title = "EditorX"
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        size = Dimension(1400, 900)
        setLocationRelativeTo(null)
        minimumSize = Dimension(800, 600)

        if (isMacOS()) {
            // 启用 macOS 外观
            System.setProperty("apple.laf.useScreenMenuBar", "true")
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "EditorX")

            // 保留原生窗口装饰，但启用全窗口内容模式
//            rootPane.putClientProperty("apple.awt.fullWindowContent", true)
//            rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
//            rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        }
    }

    private fun isMacOS(): Boolean {
        return System.getProperty("os.name").lowercase().contains("mac")
    }

    private fun setupLayout() {
        // 布局
        layout = BorderLayout()
        // 正确安装菜单栏，避免某些 LAF 下作为普通组件加入导致不显示
        jMenuBar = titleBar

        add(toolBar, BorderLayout.NORTH)
        add(activityBar, BorderLayout.WEST)
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
            // 同步SideBar内部状态与分割条位置
            sideBar.syncVisibilityWithDivider()

            if (visible && sideBar.getCurrentViewId() == null) {
                sideBar.preserveNextDividerOnShow()
                activityBar.activateItem(Constants.ACTIVITY_BAR_DEFAULT_ID, userInitiated = false)
            } else if (visible && sideBar.getCurrentViewId() != null) {
                // 用户手动拖拽显示：仅同步按钮高亮，不再触发切换逻辑，避免误判为已显示而反向关闭
                activityBar.highlightOnly(sideBar.getCurrentViewId()!!)
            } else if (!visible) {
                activityBar.clearActive()
            }

            // 同步更新ToolBar中的侧边栏toggle按钮
            toolBar.updateSideBarIcon(visible)
        }
    }

    private fun setupDefaultActivityBarItems() {
        activityBar.addItem(
            Constants.ACTIVITY_BAR_DEFAULT_ID,
            "Explorer",
            "icons/folder.svg",
            object : CachedGuiViewProvider() {
                override fun createView() = Explorer(this@MainWindow)
            }
        )

        activityBar.addItem(
            "search",
            "搜索",
            "icons/search.svg",
            object : CachedGuiViewProvider() {
                override fun createView() = SearchView(this@MainWindow)
            }
        )
    }

    fun showGlobalSearch() {
        // 若已处于搜索视图，仅聚焦输入框，不触发 ActivityBar 的“再次点击关闭”逻辑
        if (sideBar.getCurrentViewId() == "search" && sideBar.isActuallyVisible()) {
            (sideBar.getView("search") as? SearchView)?.focusQuery()
            return
        }
        sideBar.preserveNextDividerOnShow()
        activityBar.activateItem("search", userInitiated = false)
        (sideBar.getView("search") as? SearchView)?.focusQuery()
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
