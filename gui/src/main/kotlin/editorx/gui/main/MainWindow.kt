package editorx.gui.main

import editorx.gui.GuiEnvironment
import editorx.gui.main.editor.Editor
import editorx.gui.main.explorer.Explorer
import editorx.gui.main.menubar.MenuBar
import editorx.gui.main.sidebar.SideBar
import editorx.gui.main.statusbar.StatusBar
import editorx.gui.main.toolbar.ToolBar
import editorx.core.plugin.PluginManager
import editorx.core.plugin.PluginState
import editorx.gui.util.NoLineSplitPaneUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionAdapter
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JSplitPane

class MainWindow(val guiControl: GuiEnvironment) : JFrame() {

    // UI 组件
    val titleBar by lazy { MenuBar(this) }
    val toolBar by lazy { ToolBar(this) }
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
            }
            editor.refreshSyntaxForOpenTabs()
        }

        override fun onPluginUnloaded(pluginId: String) {
            // 插件卸载：无条件移除入口与视图
            sideBar.removeView(pluginId)
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
            dividerSize = 0  // 初始时 SideBar 关闭，隐藏拖拽条
        }
    }

    init {
        setupWindow()
        setupLayout()
        tuneSplitPanes()
        setupExplorer()
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
            rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
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
        add(horizontalSplit, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun tuneSplitPanes() {
        horizontalSplit.setUI(NoLineSplitPaneUI())
        horizontalSplit.border = javax.swing.BorderFactory.createEmptyBorder()
        // 启用连续布局，减少布局跳动
        horizontalSplit.isContinuousLayout = true

        // 当用户手动拖动分割条时，同步 SideBar 状态
        horizontalSplit.addPropertyChangeListener(javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY) { _ ->
            val visible = horizontalSplit.dividerLocation > 0
            // 同步SideBar内部状态与分割条位置
            sideBar.syncVisibilityWithDivider()
            
            // 根据 SideBar 可见性调整拖拽条大小
            horizontalSplit.dividerSize = if (visible) 4 else 0

            // 同步更新ToolBar中的侧边栏toggle按钮
            toolBar.updateSideBarIcon(visible)
        }
        
        // 添加自定义拖拽功能：在 SideBar 右边缘和 Editor 左边缘检测拖拽
        setupCustomDrag()
    }
    
    private var isDragging = false
    private var dragStartX = 0
    private var dragStartLocation = 0
    
    private fun setupCustomDrag() {
        // 在 SideBar 右边缘添加鼠标监听器
        sideBar.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                val rightEdge = sideBar.width
                val mouseX = e.x
                // 检测是否在右边缘 5 像素范围内
                if (mouseX >= rightEdge - 5 && mouseX <= rightEdge && horizontalSplit.dividerLocation > 0) {
                    isDragging = true
                    dragStartX = e.xOnScreen
                    dragStartLocation = horizontalSplit.dividerLocation
                    sideBar.cursor = java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)
                }
            }
            
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (isDragging) {
                    isDragging = false
                    sideBar.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                    editor.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                }
            }
        })
        
        sideBar.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                if (isDragging) {
                    val deltaX = e.xOnScreen - dragStartX
                    val newLocation = (dragStartLocation + deltaX).coerceAtLeast(0)
                        .coerceAtMost(horizontalSplit.width)
                    horizontalSplit.dividerLocation = newLocation
                }
            }
            
            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                val rightEdge = sideBar.width
                val mouseX = e.x
                if (mouseX >= rightEdge - 5 && mouseX <= rightEdge && horizontalSplit.dividerLocation > 0) {
                    sideBar.cursor = java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)
                } else {
                    sideBar.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                }
            }
        })
        
        // 在 Editor 左边缘添加鼠标监听器
        editor.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                val mouseX = e.x
                // 检测是否在左边缘 5 像素范围内
                if (mouseX >= 0 && mouseX <= 5 && horizontalSplit.dividerLocation > 0) {
                    isDragging = true
                    dragStartX = e.xOnScreen
                    dragStartLocation = horizontalSplit.dividerLocation
                    editor.cursor = java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)
                }
            }
            
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (isDragging) {
                    isDragging = false
                    sideBar.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                    editor.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                }
            }
        })
        
        editor.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                if (isDragging) {
                    val deltaX = e.xOnScreen - dragStartX
                    val newLocation = (dragStartLocation + deltaX).coerceAtLeast(0)
                        .coerceAtMost(horizontalSplit.width)
                    horizontalSplit.dividerLocation = newLocation
                }
            }
            
            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                val mouseX = e.x
                if (mouseX >= 0 && mouseX <= 5 && horizontalSplit.dividerLocation > 0) {
                    editor.cursor = java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)
                } else {
                    editor.cursor = java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR)
                }
            }
        })
    }

    private fun setupExplorer() {
        // 注册 Explorer，但不自动打开 SideBar（保持默认关闭状态）
        sideBar.showView("explorer", Explorer(this), autoShow = false)
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
