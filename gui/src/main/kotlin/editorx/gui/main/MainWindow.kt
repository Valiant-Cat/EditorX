package editorx.gui.main

import editorx.core.gui.GuiContext
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.util.SystemUtils
import editorx.gui.core.ShortcutIds
import editorx.gui.core.ShortcutRegistry
import editorx.gui.main.editor.Editor
import editorx.gui.main.explorer.Explorer
import editorx.gui.main.menubar.MenuBar
import editorx.gui.main.navigationbar.NavigationBar
import editorx.gui.main.sidebar.SideBar
import editorx.gui.main.statusbar.StatusBar
import editorx.gui.main.titlebar.TitleBar
import editorx.gui.main.toolbar.ToolBar
import editorx.gui.search.SearchDialog
import editorx.gui.settings.SettingsDialog
import editorx.gui.widget.NoLineSplitPaneUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionAdapter
import java.io.File
import javax.swing.*

class MainWindow(val guiContext: GuiContext) : JFrame() {

    // UI 组件
    private val _menuBar by lazy { MenuBar(this) }
    val titleBar by lazy { TitleBar(this) }
    val toolBar by lazy { ToolBar(this) }
    val sideBar by lazy { SideBar(this) }
    val editor by lazy { Editor(this) }
    val statusBar by lazy { StatusBar(this) }
    val navigationBar by lazy { NavigationBar(this) }

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
        setupShortcuts()
    }

    /**
     * 统一设置所有快捷键
     */
    private fun setupShortcuts() {
        // 双击 Shift - 全局搜索
        ShortcutRegistry.registerDoubleShortcut(
            id = ShortcutIds.Global.SEARCH,
            keyCode = KeyEvent.VK_SHIFT,
            nameKey = I18nKeys.Action.GLOBAL_SEARCH,
            descriptionKey = I18nKeys.Shortcut.GLOBAL_SEARCH,
            action = { showGlobalSearch() }
        )

        // Command+, - 打开设置
        val shortcutMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        ShortcutRegistry.registerShortcut(
            id = ShortcutIds.Global.SETTINGS,
            keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, shortcutMask),
            nameKey = I18nKeys.Toolbar.SETTINGS,
            descriptionKey = I18nKeys.Shortcut.OPEN_SETTINGS
        ) {
            showSettings()
        }

        // Command+N - 新建文件
        ShortcutRegistry.registerShortcut(
            id = ShortcutIds.Editor.NEW_FILE,
            keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask),
            nameKey = I18nKeys.Action.NEW_FILE,
            descriptionKey = I18nKeys.Action.NEW_FILE // 新建文件没有单独的描述，使用名称
        ) {
            editor.newUntitledFile()
        }

        // Command+W - 关闭当前标签页
        ShortcutRegistry.registerShortcut(
            id = ShortcutIds.Editor.CLOSE_TAB,
            keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutMask),
            nameKey = I18nKeys.Action.CLOSE,
            descriptionKey = I18nKeys.Shortcut.CLOSE_TAB
        ) {
            // 检查焦点是否在 editor 上
            val focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, editor)) {
                editor.closeCurrentTab()
            }
        }

        // Option+Command+L (macOS) 或 Alt+Ctrl+L (其他系统) - 格式化文件
        val formatShortcutMask = if (System.getProperty("os.name").lowercase().contains("mac")) {
            java.awt.event.InputEvent.ALT_DOWN_MASK or java.awt.event.InputEvent.META_DOWN_MASK
        } else {
            java.awt.event.InputEvent.ALT_DOWN_MASK or java.awt.event.InputEvent.CTRL_DOWN_MASK
        }
        ShortcutRegistry.registerShortcut(
            id = ShortcutIds.Editor.FORMAT_FILE,
            keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_L, formatShortcutMask),
            nameKey = I18nKeys.Action.FORMAT_FILE,
            descriptionKey = I18nKeys.Shortcut.FORMAT_FILE
        ) {
            // 检查焦点是否在 editor 上
            val focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, editor)) {
                editor.formatCurrentFile()
            }
        }
    }

    private fun setupWindow() {
        // 窗口属性
        title = "EditorX"
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
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

        // 将 navigationBar 和 horizontalSplit 放在一个容器中
        val centerContainer = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(navigationBar, BorderLayout.NORTH)
            add(horizontalSplit, BorderLayout.CENTER)
        }
        add(centerContainer, BorderLayout.CENTER)
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
            titleBar.updateSideBarIcon(visible)
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
            guiContext.getWorkspace().addRecentFile(file)
        }
    }


    /**
     * 显示全局搜索对话框
     */
    fun showGlobalSearch() {
        val dialog = SearchDialog(this, this)
        dialog.showDialog()
    }

    /**
     * 显示设置对话框
     */
    fun showSettings() {
        SettingsDialog.showOrBringToFront(this, guiContext, guiContext.getPluginManager(), SettingsDialog.Section.APPEARANCE)
    }

    /**
     * 更新面包屑导航栏
     */
    fun updateNavigation(currentFile: File?) {
        navigationBar.update(currentFile)
    }
}
