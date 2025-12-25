package editorx.gui.main.editor

import editorx.core.filetype.FileTypeRegistry
import editorx.core.filetype.FormatterRegistry
import editorx.core.toolchain.JadxTool
import editorx.core.toolchain.SmaliTool
import editorx.gui.core.theme.ThemeManager
import editorx.gui.main.MainWindow
import editorx.gui.main.explorer.ExplorerIcons
import editorx.core.util.IconUtils
import org.fife.ui.rtextarea.RTextScrollPane
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.dnd.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class Editor(private val mainWindow: MainWindow) : JPanel() {
    companion object {
        private val logger = LoggerFactory.getLogger(Editor::class.java)
        private val smaliWorkerCounter = AtomicInteger(0)
        private const val SMALI_TAB_TITLE = "Smali"
        private const val CODE_TAB_TITLE = "Code"
        private const val SMALI_PROGRESS_MESSAGE = "Smali → Java 转换中..."
    }

    private val fileToTab = mutableMapOf<File, Int>()
    private val tabToFile = mutableMapOf<Int, File>()
    private val tabbedPane = JTabbedPane()
    private val tabTextAreas = mutableMapOf<Int, TextArea>()
    private val dirtyTabs = mutableSetOf<Int>()
    private val originalTextByIndex = mutableMapOf<Int, String>()
    private var untitledCounter = 1
    
    // AndroidManifest 底部视图
    private var manifestViewTabs: JTabbedPane? = null
    private var isManifestMode = false
    private var manifestFile: File? = null
    private var manifestOriginalContent: String? = null
    
    // Smali 底部视图
    private var smaliViewTabs: JTabbedPane? = null
    private var isSmaliMode = false
    private var smaliFile: File? = null
    private var smaliOriginalContent: String? = null
    private val smaliTabState = mutableMapOf<File, Int>()
    private data class SmaliJavaCacheEntry(
        val sourceLastModified: Long,
        val sourceLength: Long,
        val javaContent: String
    )

    private val smaliJavaContentCache = mutableMapOf<File, SmaliJavaCacheEntry>()
    private val smaliConversionTasks = ConcurrentHashMap<File, Future<*>>()
    private val smaliExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "SmaliCodeWorker-${smaliWorkerCounter.incrementAndGet()}").apply {
            isDaemon = true
        }
    }
    private var smaliProgressVisible = false
    
    private val bottomContainer = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        isVisible = true
    }
    private val findReplaceBars = mutableMapOf<File, FindReplaceBar>()
    private val smaliLoadingPanels = mutableMapOf<File, JPanel>()
    private val welcomeView = editorx.gui.main.welcome.WelcomeView(mainWindow)
    private val editorContentPanel = JPanel(BorderLayout()).apply {
        add(tabbedPane, BorderLayout.CENTER)
        add(bottomContainer, BorderLayout.SOUTH)
    }

    private class TabContent(val scrollPane: RTextScrollPane) : JPanel(BorderLayout()) {
        val topSlot: JPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        }

        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            add(topSlot, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    init {
        // 设置JPanel的布局
        layout = java.awt.BorderLayout()
        updateTheme()
        
        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }

        // 配置内部的JTabbedPane
        tabbedPane.apply {
            tabPlacement = JTabbedPane.TOP
            // 单行展示，超出宽度时可滚动
            tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
            border = javax.swing.BorderFactory.createEmptyBorder()

            // 设置标签页左对齐 & 移除内容区边框
            setUI(object : javax.swing.plaf.basic.BasicTabbedPaneUI() {
                override fun getTabRunCount(tabPane: javax.swing.JTabbedPane): Int {
                    return 1 // 强制单行显示
                }

                override fun getTabRunOverlay(placement: Int): Int {
                    return 0 // 无重叠
                }

                override fun getTabInsets(placement: Int, tabIndex: Int): java.awt.Insets {
                    return java.awt.Insets(3, 6, 3, 6) // 调整标签页内边距
                }

                override fun getTabAreaInsets(placement: Int): java.awt.Insets {
                    return java.awt.Insets(0, 0, 0, 0) // 移除标签区域边距
                }

                override fun paintContentBorder(g: java.awt.Graphics?, placement: Int, selectedIndex: Int) {
                    // 不绘制内容区边框，避免底部与右侧出现黑线
                }
            })
        }

        // 根据工作区状态显示欢迎界面或编辑器内容
        updateEditorContent()

        // 注册快捷键：Command+N (macOS) 或 Ctrl+N (其他系统) 新建文件
        // 在Editor本身注册，这样在WelcomeView显示时也能响应
        val shortcutMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask),
            "editor.newFile"
        )
        actionMap.put("editor.newFile", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                newUntitledFile()
            }
        })
        
        // 同时在tabbedPane上注册，确保在编辑器内也能响应
        tabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask),
            "editor.newFile"
        )
        tabbedPane.actionMap.put("editor.newFile", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                newUntitledFile()
            }
        })

        // 注册快捷键：Command+W (macOS) 或 Ctrl+W (其他系统) 关闭标签页
        tabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutMask),
            "editor.closeTab"
        )
        tabbedPane.actionMap.put("editor.closeTab", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val currentIndex = tabbedPane.selectedIndex
                if (currentIndex >= 0) {
                    closeTab(currentIndex)
                }
            }
        })

        // 注册快捷键：Option+Command+L (macOS) 或 Alt+Ctrl+L (其他系统) 格式化代码
        val formatShortcutMask = if (System.getProperty("os.name").lowercase().contains("mac")) {
            InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK
        } else {
            InputEvent.ALT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK
        }
        tabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_L, formatShortcutMask),
            "editor.formatCode"
        )
        tabbedPane.actionMap.put("editor.formatCode", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                formatCurrentFile()
            }
        })

        // 切换标签时更新状态栏与事件
        tabbedPane.addChangeListener {
            val file = getCurrentFile()
            mainWindow.statusBar.setFileInfo(file?.name ?: "", file?.let { it.length().toString() + " B" })
            mainWindow.statusBar.updateNavigation(file)

            // 更新行号和列号显示
            if (file != null) {
                val textArea = getCurrentTextArea()
                if (textArea != null) {
                    val caretPos = textArea.caretPosition
                    val line = try {
                        textArea.getLineOfOffset(caretPos) + 1
                    } catch (_: Exception) {
                        1
                    }
                    val col = caretPos - textArea.getLineStartOffsetOfCurrentLine() + 1
                    mainWindow.statusBar.setLineColumn(line, col)
                }
            } else {
                // 没有文件打开时隐藏行号和列号
                mainWindow.statusBar.hideLineColumn()
            }

            updateTabHeaderStyles()
            
            // 检测是否为 AndroidManifest.xml，显示/隐藏底部视图标签
            updateManifestViewTabs(file)
            
            // 检测是否为 smali 文件，显示/隐藏底部视图标签
            updateSmaliViewTabs(file)

            // 文件内查找条：切换标签后同步高亮
            file?.let { currentFile ->
                val bar = findReplaceBars[currentFile]
                if (bar?.isVisible == true) {
                    attachFindBar(currentFile, bar)
                    bar.onActiveEditorChanged()
                }
            }
        }

        // 设置拖放支持 - 确保整个Editor区域都支持拖放
        enableDropTarget()
        // 同时在 tabbedPane 表面也安装 DropTarget，避免已有文件时事件落到子组件而丢失
        installFileDropTarget(tabbedPane)

        // 标签页右键菜单
        installTabContextMenu()

        // 设置TransferHandler来处理拖放
        transferHandler = object : javax.swing.TransferHandler() {
            override fun canImport(support: javax.swing.TransferHandler.TransferSupport): Boolean {
                return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
            }

            override fun importData(support: javax.swing.TransferHandler.TransferSupport): Boolean {
                if (!canImport(support)) {
                    return false
                }

                try {
                    val transferable = support.transferable
                    val dataFlavor = java.awt.datatransfer.DataFlavor.javaFileListFlavor

                    if (transferable.isDataFlavorSupported(dataFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val fileList = transferable.getTransferData(dataFlavor) as List<File>

                        // 打开所有拖入的文件
                        for (file in fileList) {
                            if (file.isFile && file.canRead()) {
                                openFile(file)
                            }
                        }

                        return true
                    }
                } catch (e: Exception) {
                    logger.warn("拖放导入文件失败", e)
                }

                return false
            }
        }
    }
    
    private fun updateTheme() {
        background = ThemeManager.currentTheme.editorBackground
        revalidate()
        repaint()
    }
    
    private fun updateEditorContent() {
        removeAll()
        val workspaceRoot = mainWindow.guiControl.workspace.getWorkspaceRoot()
        // 如果没有工作区且没有打开的标签页，显示欢迎界面
        if (workspaceRoot == null && tabbedPane.tabCount == 0) {
            welcomeView.refreshContent()
            add(welcomeView, BorderLayout.CENTER)
        } else {
            add(editorContentPanel, BorderLayout.CENTER)
        }
        revalidate()
        repaint()
    }
    
    fun showEditorContent() {
        updateEditorContent()
    }

    // VSCode 风格的 Tab 头：固定槽位 + hover/选中显示 close 按钮
    private fun createVSCodeTabHeader(file: File): JPanel = JPanel().apply {
        layout = java.awt.BorderLayout(); isOpaque = true; background = Color.WHITE
        val header = this
        var hovering = false
        val titleLabel = JLabel(file.name).apply {
            border = BorderFactory.createEmptyBorder(0, 8, 0, 6)
            horizontalAlignment = JLabel.LEFT
            icon = resolveTabIcon(file)
            iconTextGap = 6
        }
        add(titleLabel, java.awt.BorderLayout.CENTER)

        val closeSlot = object : JPanel(CardLayout()) {
            var highlight = false
            override fun paintComponent(g: java.awt.Graphics) {
                if (highlight) {
                    val g2 = g.create() as java.awt.Graphics2D
                    try {
                        g2.setRenderingHint(
                            java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                        )
                        // 极淡的白色半透明填充 + 细描边，避免出现深色块
                        g2.color = java.awt.Color(255, 255, 255, 20)
                        g2.fillRoundRect(0, 0, width, height, 6, 6)
                        g2.color = ThemeManager.separator
                        g2.stroke = java.awt.BasicStroke(1f)
                        g2.drawRoundRect(1, 1, width - 2, height - 2, 6, 6)
                    } finally {
                        g2.dispose()
                    }
                }
                super.paintComponent(g)
            }
        }.apply {
            isOpaque = false
            preferredSize = Dimension(18, 18)
            minimumSize = Dimension(18, 18)
            maximumSize = Dimension(18, 18)
        }
        val empty = JPanel().apply { isOpaque = false }
        val closeBtn = JLabel("×").apply {
            font = font.deriveFont(Font.PLAIN, 13f)
            foreground = ThemeManager.editorTabCloseDefault
            horizontalAlignment = JLabel.CENTER
            verticalAlignment = JLabel.CENTER
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    hovering = true
                    closeSlot.highlight = true
                    closeSlot.repaint()
                    foreground = ThemeManager.editorTabCloseSelected
                    cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                }

                override fun mouseExited(e: MouseEvent) {
                    closeSlot.highlight = false
                    closeSlot.repaint()
                    hovering = false
                    val idxLocal = tabbedPane.indexOfTabComponent(header)
                    val selected = (idxLocal == tabbedPane.selectedIndex)
                    foreground =
                        if (selected) ThemeManager.editorTabCloseSelected else ThemeManager.editorTabCloseDefault
                    cursor = java.awt.Cursor.getDefaultCursor()
                    val inside = header.mousePosition != null
                    if (idxLocal >= 0 && idxLocal != tabbedPane.selectedIndex && !inside && !hovering) {
                        (closeSlot.layout as CardLayout).show(closeSlot, "empty")
                    }
                }

                override fun mousePressed(e: MouseEvent) {
                    val idx3 = tabbedPane.indexOfTabComponent(header)
                    if (idx3 >= 0) {
                        closeTab(idx3); e.consume()
                    }
                }
            })
        }
        closeSlot.add(empty, "empty")
        closeSlot.add(closeBtn, "btn")
        (closeSlot.layout as CardLayout).show(closeSlot, "empty")
        add(closeSlot, java.awt.BorderLayout.EAST)

        // 点击整个槽位也可关闭（当按钮可见时）
        closeSlot.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (closeBtn.isShowing) {
                    val idx = tabbedPane.indexOfTabComponent(header)
                    if (idx >= 0) {
                        closeTab(idx); e.consume()
                    }
                }
            }

            override fun mouseEntered(e: MouseEvent) {
                hovering = true;
                val idx =
                    tabbedPane.indexOfTabComponent(header); if (idx >= 0 && idx != tabbedPane.selectedIndex) (closeSlot.layout as CardLayout).show(
                    closeSlot,
                    "btn"
                ); closeSlot.highlight = true; closeSlot.repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                hovering = false
                closeSlot.highlight = false; closeSlot.repaint()
                val idx = tabbedPane.indexOfTabComponent(header)
                val inside = header.mousePosition != null
                if (idx >= 0 && idx != tabbedPane.selectedIndex && !inside && !hovering) {
                    (closeSlot.layout as CardLayout).show(closeSlot, "empty")
                }
            }
        })

        putClientProperty("titleLabel", titleLabel)
        putClientProperty("closeSlot", closeSlot)
        putClientProperty("closeLabel", closeBtn)

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hovering = true
                val idx = tabbedPane.indexOfTabComponent(header)
                if (idx >= 0 && idx != tabbedPane.selectedIndex) (closeSlot.layout as CardLayout).show(closeSlot, "btn")
            }

            override fun mouseExited(e: MouseEvent) {
                hovering = false
                val idx = tabbedPane.indexOfTabComponent(header)
                val inside = header.mousePosition != null
                if (idx >= 0 && idx != tabbedPane.selectedIndex && !inside && !hovering) {
                    (closeSlot.layout as CardLayout).show(closeSlot, "empty")
                }
            }

            override fun mousePressed(e: MouseEvent) {
                val idx = tabbedPane.indexOfTabComponent(header)
                if (idx >= 0) tabbedPane.selectedIndex = idx
            }
        })

        // 在整个 header 内移动时也持续显示关闭按钮
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                hovering = true
                val idx = tabbedPane.indexOfTabComponent(header)
                if (idx >= 0 && idx != tabbedPane.selectedIndex) (closeSlot.layout as CardLayout).show(closeSlot, "btn")
            }
        })

        titleLabel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val idx = tabbedPane.indexOfTabComponent(header)
                if (idx >= 0) tabbedPane.selectedIndex = idx
            }

            override fun mouseEntered(e: MouseEvent) {
                hovering = true
                val idx = tabbedPane.indexOfTabComponent(header)
                if (idx >= 0 && idx != tabbedPane.selectedIndex) (closeSlot.layout as CardLayout).show(closeSlot, "btn")
            }

            override fun mouseExited(e: MouseEvent) {
                hovering = false
                val idx = tabbedPane.indexOfTabComponent(header)
                val inside = header.mousePosition != null
                if (idx >= 0 && idx != tabbedPane.selectedIndex && !inside && !hovering) (closeSlot.layout as CardLayout).show(
                    closeSlot,
                    "empty"
                )
            }
        })

        titleLabel.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                hovering = true
                val idx = tabbedPane.indexOfTabComponent(header)
                if (idx >= 0 && idx != tabbedPane.selectedIndex) (closeSlot.layout as CardLayout).show(closeSlot, "btn")
            }
        })

    }

    fun openFile(file: File) {
        if (fileToTab.containsKey(file)) {
            tabbedPane.selectedIndex = fileToTab[file]!!
            mainWindow.statusBar.updateNavigation(file)
            return
        }
        
        // 检测是否为 Android 压缩包文件（xapk、aab、aar）
        val fileType = FileTypeRegistry.getFileTypeByFileName(file.name)
        if (fileType?.getName() == "android-archive") {
            openArchiveFile(file)
            return
        }
        
        val textArea = TextArea().apply {
            font = Font("Consolas", Font.PLAIN, 14)
            addCaretListener {
                val caretPos = caretPosition
                val line = try {
                    getLineOfOffset(caretPos) + 1
                } catch (_: Exception) {
                    1
                }
                val col = caretPos - getLineStartOffsetOfCurrentLine(this) + 1
                mainWindow.statusBar.setLineColumn(line, col)
            }
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = recomputeDirty()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = recomputeDirty()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = recomputeDirty()
                private fun recomputeDirty() {
                    if (getClientProperty("suppressDirty") == true) return
                    val scrollComp = this@apply.parent?.parent as? java.awt.Component ?: return
                    val index = getTabIndexForComponent(scrollComp)
                    if (index < 0) return
                    val original = originalTextByIndex[index]
                    val isDirty = original != this@apply.text
                    if (isDirty) dirtyTabs.add(index) else dirtyTabs.remove(index)
                    updateTabTitle(index)
                    updateTabHeaderStyles()
                }
            })

            // Ctrl+B (or Cmd+B on macOS) to attempt navigation (goto definition)
            val key = if (System.getProperty("os.name").lowercase().contains("mac"))
                KeyStroke.getKeyStroke(KeyEvent.VK_B, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
            else KeyStroke.getKeyStroke(KeyEvent.VK_B, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
            getInputMap(JComponent.WHEN_FOCUSED).put(key, "gotoDefinition")
            actionMap.put("gotoDefinition", object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    val scrollComp = this@apply.parent?.parent as? java.awt.Component ?: return
                    val index = getTabIndexForComponent(scrollComp)
                    val f = tabToFile[index] ?: return
                    attemptNavigate(f, this@apply)
                }
            })

            val shortcutMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutMask), "editor.find")
            actionMap.put("editor.find", object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    this@Editor.showFindBar()
                }
            })

            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcutMask), "editor.replace")
            actionMap.put("editor.replace", object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    this@Editor.showReplaceBar()
                }
            })

            // 安装右键菜单
            val textAreaForMenu = this
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showTextAreaContextMenu(textAreaForMenu, e.x, e.y)
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showTextAreaContextMenu(textAreaForMenu, e.x, e.y)
                    }
                }
            })
        }
        try {
            // 在装载初始文件内容时静默，不触发脏标记
            textArea.putClientProperty("suppressDirty", true)
            textArea.text = Files.readString(file.toPath())
            textArea.discardAllEdits()
            mainWindow.statusBar.setFileInfo(file.name, Files.size(file.toPath()).toString() + " B")
            mainWindow.guiControl.workspace.addRecentFile(file)

            // 在文本加载完成后应用语法高亮
            SwingUtilities.invokeLater {
                textArea.detectSyntax(file)
            }
        } catch (e: Exception) {
            textArea.text = "无法读取文件: ${e.message}"
            textArea.isEditable = false
        }
        val scroll = RTextScrollPane(textArea).apply {
            border = javax.swing.BorderFactory.createEmptyBorder()
            background = Color.WHITE
            viewport.background = Color.WHITE
        }
        // 让新建的编辑器视图也支持文件拖入
        installFileDropTarget(scroll)
        installFileDropTarget(textArea)
        val title = file.name
        tabbedPane.addTab(title, resolveTabIcon(file), TabContent(scroll), null)
        val index = tabbedPane.tabCount - 1
        fileToTab[file] = index
        tabToFile[index] = file
        tabTextAreas[index] = textArea
        val closeButton = createVSCodeTabHeader(file)
        tabbedPane.setTabComponentAt(index, closeButton)
        attachPopupToHeader(closeButton)
        tabbedPane.selectedIndex = index
        // 打开后默认滚动到左上角，光标置于文件开头
        SwingUtilities.invokeLater {
            runCatching {
                textArea.caretPosition = 0
                textArea.scrollRectToVisible(Rectangle(0, 0, 1, 1))
                scroll.verticalScrollBar.value = 0
                scroll.horizontalScrollBar.value = 0
            }
        }
        // 记录原始内容，清除脏标记并开启后续脏检测
        originalTextByIndex[index] = textArea.text
        dirtyTabs.remove(index)
        textArea.putClientProperty("suppressDirty", false)
        updateTabHeaderStyles()
        mainWindow.statusBar.updateNavigation(file)
        
        // 检测是否为 AndroidManifest.xml，显示/隐藏底部视图标签
        updateManifestViewTabs(file)
        
        // 检测是否为 smali 文件，显示/隐藏底部视图标签
        updateSmaliViewTabs(file)
        
        // 打开文件后显示编辑器内容（隐藏欢迎界面）
        showEditorContent()
    }

    fun newUntitledFile() {
        // 创建一个未命名文件标识（不创建实际文件）
        val untitledFileName = "untitled-${untitledCounter++}"
        val untitledFile = File(untitledFileName)
        
        val textArea = TextArea().apply {
            font = Font("Consolas", Font.PLAIN, 14)
            text = ""  // 空内容
            addCaretListener {
                val caretPos = caretPosition
                val line = try {
                    getLineOfOffset(caretPos) + 1
                } catch (_: Exception) {
                    1
                }
                val col = caretPos - getLineStartOffsetOfCurrentLine(this) + 1
                mainWindow.statusBar.setLineColumn(line, col)
            }
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = recomputeDirty()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = recomputeDirty()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = recomputeDirty()
                private fun recomputeDirty() {
                    if (getClientProperty("suppressDirty") == true) return
                    val scrollComp = this@apply.parent?.parent as? java.awt.Component ?: return
                    val index = getTabIndexForComponent(scrollComp)
                    if (index < 0) return
                    val original = originalTextByIndex[index]
                    val isDirty = original != this@apply.text
                    if (isDirty) dirtyTabs.add(index) else dirtyTabs.remove(index)
                    updateTabTitle(index)
                    updateTabHeaderStyles()
                }
            })

            val shortcutMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutMask), "editor.find")
            actionMap.put("editor.find", object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    this@Editor.showFindBar()
                }
            })

            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcutMask), "editor.replace")
            actionMap.put("editor.replace", object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    this@Editor.showReplaceBar()
                }
            })

            // 安装右键菜单
            val textAreaForMenu = this
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showTextAreaContextMenu(textAreaForMenu, e.x, e.y)
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showTextAreaContextMenu(textAreaForMenu, e.x, e.y)
                    }
                }
            })
        }
        
        val scroll = RTextScrollPane(textArea).apply {
            border = javax.swing.BorderFactory.createEmptyBorder()
            background = Color.WHITE
            viewport.background = Color.WHITE
        }
        installFileDropTarget(scroll)
        installFileDropTarget(textArea)
        
        val title = untitledFileName
        tabbedPane.addTab(title, resolveTabIcon(untitledFile), TabContent(scroll), null)
        val index = tabbedPane.tabCount - 1
        fileToTab[untitledFile] = index
        tabToFile[index] = untitledFile
        tabTextAreas[index] = textArea
        val closeButton = createVSCodeTabHeader(untitledFile)
        tabbedPane.setTabComponentAt(index, closeButton)
        attachPopupToHeader(closeButton)
        tabbedPane.selectedIndex = index
        
        SwingUtilities.invokeLater {
            runCatching {
                textArea.caretPosition = 0
                textArea.scrollRectToVisible(Rectangle(0, 0, 1, 1))
                scroll.verticalScrollBar.value = 0
                scroll.horizontalScrollBar.value = 0
            }
        }
        
        originalTextByIndex[index] = ""
        dirtyTabs.remove(index)
        textArea.putClientProperty("suppressDirty", false)
        updateTabHeaderStyles()
        mainWindow.statusBar.setFileInfo(title, "0 B")
        mainWindow.statusBar.setLineColumn(1, 1)
        
        // 聚焦到编辑器
        SwingUtilities.invokeLater {
            textArea.requestFocusInWindow()
        }
        
        // 新建文件后显示编辑器内容（隐藏欢迎界面）
        showEditorContent()
    }

    fun openFileAndSelect(file: File, line: Int, column: Int, length: Int) {
        // 统一在 EDT 上执行，避免跨线程访问 Swing 组件
        SwingUtilities.invokeLater {
            openFile(file)
            // 等 openFile 内部的默认“滚动到顶部”任务执行完毕后再定位
            SwingUtilities.invokeLater {
                val idx = fileToTab[file] ?: return@invokeLater
                tabbedPane.selectedIndex = idx
                val textArea = tabTextAreas[idx] ?: return@invokeLater

                runCatching {
                    val lineIndex = (line - 1).coerceAtLeast(0)
                    val safeLineStart = textArea.getLineStartOffset(lineIndex)
                    val docLen = textArea.document.length
                    val start = (safeLineStart + column).coerceIn(0, docLen)
                    val end = (start + length).coerceIn(0, docLen)
                    textArea.caretPosition = start
                    if (end > start) textArea.select(start, end)
                    textArea.requestFocusInWindow()
                    val rect = textArea.modelToView2D(start).bounds
                    textArea.scrollRectToVisible(rect)
                }
            }
        }
    }
    
    fun showFindBar() {
        val file = getCurrentFile() ?: return
        val findBar = getFindBar(file)
        attachFindBar(file, findBar)
        val initial = getCurrentTextArea()?.selectedText
            ?.takeIf { it.isNotBlank() && !it.contains('\n') && !it.contains('\r') }
        findBar.open(FindReplaceBar.Mode.FIND, initial)
    }
    
    fun showReplaceBar() {
        val file = getCurrentFile() ?: return
        val findBar = getFindBar(file)
        attachFindBar(file, findBar)
        val initial = getCurrentTextArea()?.selectedText
            ?.takeIf { it.isNotBlank() && !it.contains('\n') && !it.contains('\r') }
        findBar.open(FindReplaceBar.Mode.REPLACE, initial)
    }

    /**
     * 当插件启停/卸载导致文件类型或语法高亮注册变化时，刷新所有已打开标签页的语法样式。
     */
    fun refreshSyntaxForOpenTabs() {
        SwingUtilities.invokeLater {
            tabTextAreas.forEach { (idx, textArea) ->
                val file = tabToFile[idx] ?: return@forEach
                runCatching { textArea.detectSyntax(file) }
            }
        }
    }

    private fun getTabIndexForComponent(component: java.awt.Component): Int {
        var current: java.awt.Component? = component
        while (current != null && current.parent !== tabbedPane) {
            current = current.parent
        }
        return if (current == null) -1 else tabbedPane.indexOfComponent(current)
    }

    private fun attemptNavigate(file: File, textArea: TextArea) {
//        try {
//            val vf = LocalVirtualFile.of(file)
//            val provider = NavigationRegistry.findFirstForFile(vf)
//            if (provider == null) {
//                mainWindow.statusBar.setMessage("无可用跳转处理器")
//                return
//            }
//            val caret = textArea.caretPosition
//            val target = provider.gotoDefinition(vf, caret, textArea.text)
//            if (target == null) {
//                mainWindow.statusBar.setMessage("未找到跳转目标")
//                return
//            }
//            val targetFile = when (target.file) {
//                is LocalVirtualFile -> (target.file as LocalVirtualFile).toFile()
//                else -> null
//            }
//            if (targetFile != null) {
//                openFile(targetFile)
//                val idx = fileToTab[targetFile]
//                if (idx != null) {
//                    val ta = tabTextAreas[idx]
//                    if (ta != null) {
//                        val pos = target.offset.coerceIn(0, ta.document.length)
//                        ta.caretPosition = pos
//                        val r = ta.modelToView2D(pos)
//                        if (r != null) ta.scrollRectToVisible(r.bounds)
//                    }
//                }
//            } else {
//                mainWindow.statusBar.setMessage("跳转目标不可打开")
//            }
//        } catch (e: Exception) {
//            mainWindow.statusBar.setMessage("跳转失败: ${e.message}")
//        }
    }

    private fun createTabHeader(file: File): JPanel = JPanel().apply {
        layout = java.awt.BorderLayout(); isOpaque = true; background = Color.WHITE
        val titleLabel = JLabel(file.name).apply {
            border = BorderFactory.createEmptyBorder(0, 8, 0, 6)
            horizontalAlignment = JLabel.LEFT
            icon = resolveTabIcon(file)
            iconTextGap = 6
        }
        add(titleLabel, java.awt.BorderLayout.CENTER)

        // 右侧固定槽位 (18x18)，内部用 CardLayout 切换“按钮 / 占位”
        val closeSlot = JPanel(CardLayout()).apply {
            isOpaque = false
            preferredSize = Dimension(18, 18)
            minimumSize = Dimension(18, 18)
            maximumSize = Dimension(18, 18)
        }
        val filler = JPanel().apply { isOpaque = false }
        val closeBtn = JLabel("×").apply {
            font = font.deriveFont(Font.PLAIN, 13f)
            foreground = ThemeManager.editorTabCloseDefault
            horizontalAlignment = JLabel.CENTER
            verticalAlignment = JLabel.CENTER
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    closeSlot.isOpaque = true
                    closeSlot.background = ThemeManager.editorTabCloseHoverBackground
                    foreground = ThemeManager.editorTabCloseSelected
                    cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                }

                override fun mouseExited(e: MouseEvent) {
                    closeSlot.isOpaque = false
                    closeSlot.background = null
                    val idx = tabbedPane.indexOfTabComponent(this@apply)
                    val selected = (idx == tabbedPane.selectedIndex)
                    foreground =
                        if (selected) ThemeManager.editorTabCloseSelected else ThemeManager.editorTabCloseDefault
                    cursor = java.awt.Cursor.getDefaultCursor()
                }

                override fun mousePressed(e: MouseEvent) {
                    val idx = tabbedPane.indexOfTabComponent(this@apply)
                    if (idx >= 0) closeTab(idx)
                }
            })
        }
        closeSlot.layout = CardLayout()
        closeSlot.add(filler, "empty")
        closeSlot.add(closeBtn, "btn")
        (closeSlot.layout as CardLayout).show(closeSlot, "empty")
        add(closeSlot, java.awt.BorderLayout.EAST)

        // 保存引用
        putClientProperty("titleLabel", titleLabel)
        putClientProperty("closeSlot", closeSlot)
        putClientProperty("closeLabel", closeBtn)

        // 标签头 hover：未选中时显示按钮/离开隐藏；点击选择
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                val idx = tabbedPane.indexOfTabComponent(this@apply)
                if (idx >= 0 && idx != tabbedPane.selectedIndex) {
                    (closeSlot.layout as CardLayout).show(closeSlot, "btn")
                }
            }

            override fun mouseExited(e: MouseEvent) {
                val idx = tabbedPane.indexOfTabComponent(this@apply)
                if (idx >= 0 && idx != tabbedPane.selectedIndex) {
                    (closeSlot.layout as CardLayout).show(closeSlot, "empty")
                }
            }

            override fun mousePressed(e: MouseEvent) {
                val idx = tabbedPane.indexOfTabComponent(this@apply)
                if (idx >= 0) tabbedPane.selectedIndex = idx
            }
        })

        // 标题点击也切换选中
        titleLabel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val idx = tabbedPane.indexOfTabComponent(this@apply)
                if (idx >= 0) tabbedPane.selectedIndex = idx
            }
        })
    }

    private fun updateTabHeaderStyles() {
        for (i in 0 until tabbedPane.tabCount) {
            val comp = tabbedPane.getTabComponentAt(i) as? JPanel ?: continue
            val isSelected = (i == tabbedPane.selectedIndex)
            comp.isOpaque = true
            comp.background = Color.WHITE

            // 文本颜色区分选中与未选中
            val label = comp.getClientProperty("titleLabel") as? JLabel
            label?.foreground =
                if (isSelected) ThemeManager.editorTabSelectedForeground else ThemeManager.editorTabForeground
            label?.font =
                (label?.font ?: Font("Dialog", Font.PLAIN, 12)).deriveFont(if (isSelected) Font.BOLD else Font.PLAIN)

            // 关闭按钮显示策略（CardLayout 切换保持占位）
            val slot = comp.getClientProperty("closeSlot") as? JPanel
            val close = comp.getClientProperty("closeLabel") as? JLabel
            if (slot != null && close != null) {
                val cl = slot.layout as CardLayout
                if (isSelected) {
                    cl.show(slot, "btn")
                    close.foreground = ThemeManager.editorTabCloseSelected
                } else {
                    cl.show(slot, "empty")
                    close.foreground = ThemeManager.editorTabCloseDefault
                }
                slot.isOpaque = false
                slot.background = null
            }

            // 若存在固定槽位，依据选中态切换卡片，确保占位
            run {
                val slot = comp.getClientProperty("closeSlot") as? JPanel
                val cl = slot?.layout as? CardLayout
                if (slot != null && cl != null) {
                    if (isSelected) cl.show(slot, "btn") else cl.show(slot, "empty")
                    slot.isOpaque = false; slot.background = null
                }
            }

            // 不再使用下划线，保持背景一致（可留 2px 空白保持高度统一）
            comp.border = BorderFactory.createEmptyBorder(0, 0, 2, 0)
        }
    }

    private fun closeTab(index: Int) {
        if (index >= 0 && index < tabbedPane.tabCount) {
            // 检查是否有未保存的更改
            if (dirtyTabs.contains(index)) {
                val file = tabToFile[index]
                val fileName = file?.name ?: "未命名文件"
                
                // 切换到要关闭的标签页（如果需要保存）
                val wasSelected = tabbedPane.selectedIndex == index
                if (!wasSelected) {
                    tabbedPane.selectedIndex = index
                }
                
                // 显示保存提示对话框
                val options = arrayOf("保存", "不保存", "取消")
                val result = JOptionPane.showOptionDialog(
                    mainWindow,
                    "您对 \"$fileName\" 的更改尚未保存。\n如果不保存，您的更改将丢失。",
                    "保存更改？",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0] // 默认选择"保存"
                )
                
                when (result) {
                    JOptionPane.YES_OPTION -> {
                        // 保存文件
                        val saved = saveTab(index)
                        if (!saved) {
                            // 如果保存失败或用户取消，不关闭标签页
                            return
                        }
                    }
                    JOptionPane.NO_OPTION -> {
                        // 不保存，直接关闭
                    }
                    JOptionPane.CANCEL_OPTION, JOptionPane.CLOSED_OPTION -> {
                        // 取消关闭
                        return
                    }
                }
            }
            
            val file = tabToFile[index]
            tabbedPane.removeTabAt(index)
            file?.let {
                fileToTab.remove(it)
                smaliTabState.remove(it)
                smaliJavaContentCache.remove(it)
                cancelSmaliConversion(it)
                findReplaceBars.remove(it)?.let { bar ->
                    (bar.parent as? Container)?.remove(bar)
                }
                smaliLoadingPanels.remove(it)?.let { panel ->
                    (panel.parent as? Container)?.remove(panel)
                }
            }
            tabToFile.remove(index)
            tabTextAreas.remove(index)
            originalTextByIndex.remove(index)
            dirtyTabs.remove(index)

            val newTabToFile = mutableMapOf<Int, File>()
            tabToFile.forEach { (oldIdx, f) ->
                val newIdx = if (oldIdx > index) oldIdx - 1 else oldIdx
                newTabToFile[newIdx] = f
            }

            val newTabTextAreas = mutableMapOf<Int, TextArea>()
            tabTextAreas.forEach { (oldIdx, ta) ->
                val newIdx = if (oldIdx > index) oldIdx - 1 else oldIdx
                newTabTextAreas[newIdx] = ta
            }

            val newOriginal = mutableMapOf<Int, String>()
            originalTextByIndex.forEach { (oldIdx, text) ->
                val newIdx = if (oldIdx > index) oldIdx - 1 else oldIdx
                newOriginal[newIdx] = text
            }

            val newDirty = mutableSetOf<Int>()
            dirtyTabs.forEach { oldIdx ->
                newDirty.add(if (oldIdx > index) oldIdx - 1 else oldIdx)
            }

            tabToFile.clear()
            tabToFile.putAll(newTabToFile)
            tabTextAreas.clear()
            tabTextAreas.putAll(newTabTextAreas)
            originalTextByIndex.clear()
            originalTextByIndex.putAll(newOriginal)
            dirtyTabs.clear()
            dirtyTabs.addAll(newDirty)

            fileToTab.clear()
            newTabToFile.forEach { (i, f) -> fileToTab[f] = i }

            mainWindow.statusBar.updateNavigation(getCurrentFile())
            
            // 如果所有标签都关闭了，显示欢迎界面
            if (tabbedPane.tabCount == 0) {
                updateEditorContent()
            }
        }
    }

    fun getCurrentFile(): File? = if (tabbedPane.selectedIndex >= 0) tabToFile[tabbedPane.selectedIndex] else null

    fun hasUnsavedChanges(): Boolean = dirtyTabs.isNotEmpty()

    fun saveCurrent() {
        val idx = tabbedPane.selectedIndex
        if (idx < 0) return
        saveTab(idx)
    }

    /**
     * 保存指定索引的标签页
     * @return true 如果保存成功或不需要保存，false 如果用户取消保存
     */
    private fun saveTab(index: Int): Boolean {
        if (index < 0 || index >= tabbedPane.tabCount) return false
        val file = tabToFile[index]
        val ta = tabTextAreas[index] ?: return true
        
        // 如果文件存在且可写，直接保存
        if (file != null && file.exists() && file.canWrite()) {
            runCatching { Files.writeString(file.toPath(), ta.text) }
            dirtyTabs.remove(index)
            originalTextByIndex[index] = ta.text
            updateTabTitle(index)
            mainWindow.statusBar.updateNavigation(file)
            mainWindow.statusBar.showSuccess("已保存: ${file.name}")
            return true
        }
        
        // 文件不存在（未命名文件）或不可写，使用系统文件选择器另存为
        val fileDialog = java.awt.FileDialog(mainWindow, "保存文件", java.awt.FileDialog.SAVE).apply {
            isMultipleMode = false
            // 如果当前文件有名称且不是未命名文件，设置为默认文件名
            if (file != null && !file.name.startsWith("untitled-") && file.exists()) {
                this.file = file.name
                directory = file.parent
            }
        }
        fileDialog.isVisible = true
        
        val fileName = fileDialog.file
        val dir = fileDialog.directory
        
        if (fileName != null && dir != null) {
            val newFile = File(dir, fileName)
            runCatching { Files.writeString(newFile.toPath(), ta.text) }
            // 更新文件映射
            tabToFile[index]?.let { fileToTab.remove(it) }
            tabToFile[index] = newFile
            fileToTab[newFile] = index
            // 先更新原始内容和脏标记，再更新标题
            originalTextByIndex[index] = ta.text
            dirtyTabs.remove(index)
            updateTabTitle(index)
            updateTabHeaderStyles()
            mainWindow.guiControl.workspace.addRecentFile(newFile)
            mainWindow.statusBar.updateNavigation(newFile)
            mainWindow.statusBar.showSuccess("已保存: ${newFile.name}")
            return true
        } else {
            // 用户取消了保存对话框
            return false
        }
    }

    fun saveCurrentAs() {
        val idx = tabbedPane.selectedIndex
        if (idx < 0) return
        val ta = tabTextAreas[idx] ?: return
        val currentFile = tabToFile[idx]
        
        // 使用系统文件选择器
        val fileDialog = java.awt.FileDialog(mainWindow, "另存为", java.awt.FileDialog.SAVE).apply {
            isMultipleMode = false
            // 如果当前文件存在，设置为默认文件名
            if (currentFile != null && currentFile.exists()) {
                file = currentFile.name
                directory = currentFile.parent
            }
        }
        fileDialog.isVisible = true
        
        val fileName = fileDialog.file
        val dir = fileDialog.directory
        
        if (fileName != null && dir != null) {
            val file = File(dir, fileName)
            runCatching { Files.writeString(file.toPath(), ta.text) }
            // Update mappings
            tabToFile[idx]?.let { fileToTab.remove(it) }
            tabToFile[idx] = file
            fileToTab[file] = idx
            // 先更新原始内容和脏标记，再更新标题
            originalTextByIndex[idx] = ta.text
            dirtyTabs.remove(idx)
            updateTabTitle(idx)
            updateTabHeaderStyles()
            mainWindow.guiControl.workspace.addRecentFile(file)
            mainWindow.statusBar.updateNavigation(file)
            mainWindow.statusBar.showSuccess("已保存: ${file.name}")
        }
    }

    private fun resolveTabIcon(file: File?): Icon? {
        val target = file ?: return ExplorerIcons.AnyType?.let { IconUtils.resizeIcon(it, 16, 16) }
        if (target.isDirectory) {
            return ExplorerIcons.Folder?.let { IconUtils.resizeIcon(it, 16, 16) }
        }
        val fileTypeIcon =
            FileTypeRegistry.getFileTypeByFileName(file.name)?.getIcon()?.let { IconUtils.resizeIcon(it, 16, 16) }
        return fileTypeIcon ?: ExplorerIcons.AnyType?.let { IconUtils.resizeIcon(it, 16, 16) }
    }

    private fun updateTabTitle(index: Int) {
        val file = tabToFile[index]
        val base = file?.name ?: "Untitled"
        val dirty = if (dirtyTabs.contains(index)) "*" else ""
        val component = tabbedPane.getTabComponentAt(index) as? JPanel
        if (component != null) {
            val label = component.getClientProperty("titleLabel") as? JLabel ?: component.getComponent(0) as? JLabel
            label?.text = dirty + base
            label?.icon = resolveTabIcon(file)
        } else {
            tabbedPane.setTitleAt(index, dirty + base)
            tabbedPane.setIconAt(index, resolveTabIcon(file))
        }
    }

    private fun getLineStartOffsetOfCurrentLine(area: TextArea): Int {
        val caretPos = area.caretPosition
        val line = area.getLineOfOffset(caretPos)
        return area.getLineStartOffset(line)
    }

    private fun installTabContextMenu() {
        fun showMenuAt(invoker: java.awt.Component, index: Int, x: Int, y: Int) {
            val menu = JPopupMenu()
            menu.add(JMenuItem("关闭").apply { addActionListener { closeTab(index) } })
            menu.add(JMenuItem("关闭其他标签").apply { addActionListener { closeOthers(index) } })
            menu.add(JMenuItem("关闭所有标签").apply { addActionListener { closeAll() } })
            menu.addSeparator()
            menu.add(JMenuItem("关闭左侧标签").apply { addActionListener { closeLeftOf(index) } })
            menu.add(JMenuItem("关闭右侧标签").apply { addActionListener { closeRightOf(index) } })
            menu.addSeparator()
            menu.add(JMenuItem("关闭未修改标签").apply { addActionListener { closeUnmodified() } })
            menu.show(invoker, x, y)
        }
        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) trigger(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) trigger(e)
            }

            private fun trigger(e: MouseEvent) {
                val idx = tabbedPane.indexAtLocation(e.x, e.y)
                if (idx < 0) return
                tabbedPane.selectedIndex = idx
                showMenuAt(tabbedPane, idx, e.x, e.y)
            }
        })
    }

    // 在任意 Tab Header 组件（含关闭按钮/标题）上安装右键菜单触发
    private fun attachPopupToHeader(header: JComponent) {
        val l = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) trigger(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) trigger(e)
            }

            private fun trigger(e: MouseEvent) {
                val idx = tabbedPane.indexOfTabComponent(header)
                if (idx < 0) return
                tabbedPane.selectedIndex = idx
                val inv = e.component as? java.awt.Component ?: header
                val menu = JPopupMenu()
                menu.add(JMenuItem("关闭").apply { addActionListener { closeTab(idx) } })
                menu.add(JMenuItem("关闭其他标签").apply { addActionListener { closeOthers(idx) } })
                menu.add(JMenuItem("关闭所有标签").apply { addActionListener { closeAll() } })
                menu.addSeparator()
                menu.add(JMenuItem("关闭左侧标签").apply { addActionListener { closeLeftOf(idx) } })
                menu.add(JMenuItem("关闭右侧标签").apply { addActionListener { closeRightOf(idx) } })
                menu.addSeparator()
                menu.add(JMenuItem("关闭未修改标签").apply { addActionListener { closeUnmodified() } })
                menu.show(inv, e.x, e.y)
            }
        }
        header.addMouseListener(l)
        header.components.forEach { it.addMouseListener(l) }
    }

    private fun closeOthers(keepIndex: Int) {
        val toClose = (0 until tabbedPane.tabCount).filter { it != keepIndex }.sortedDescending()
        toClose.forEach { closeTab(it) }
    }

    private fun closeAll() {
        val toClose = (0 until tabbedPane.tabCount).sortedDescending()
        toClose.forEach { closeTab(it) }
    }

    private fun closeLeftOf(index: Int) {
        val toClose = (0 until index).sortedDescending()
        toClose.forEach { closeTab(it) }
    }

    private fun closeRightOf(index: Int) {
        val toClose = (index + 1 until tabbedPane.tabCount).sortedDescending()
        toClose.forEach { closeTab(it) }
    }

    private fun closeUnmodified() {
        val toClose = (0 until tabbedPane.tabCount).filter { it !in dirtyTabs }.sortedDescending()
        toClose.forEach { closeTab(it) }
    }

    private fun enableDropTarget() {
        // 为整个Editor组件设置拖放支持
        DropTarget(this, object : DropTargetListener {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                if (isDragAcceptable(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                if (isDragAcceptable(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dropActionChanged(dtde: DropTargetDragEvent) {
                if (isDragAcceptable(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dragExit(dtde: DropTargetEvent) {
                // 拖放退出，无需特殊处理
            }

            override fun drop(dtde: DropTargetDropEvent) {
                if (!isDragAcceptable(dtde)) {
                    dtde.rejectDrop()
                    return
                }

                dtde.acceptDrop(DnDConstants.ACTION_COPY)

                try {
                    val transferable = dtde.transferable
                    val dataFlavors = transferable.transferDataFlavors

                    for (dataFlavor in dataFlavors) {
                        if (dataFlavor.isFlavorJavaFileListType) {
                            @Suppress("UNCHECKED_CAST")
                            val fileList = transferable.getTransferData(dataFlavor) as List<File>

                            for (file in fileList) {
                                if (file.isFile && file.canRead()) {
                                    openFile(file)
                                }
                            }

                            dtde.dropComplete(true)
                            return
                        }
                    }

                    dtde.dropComplete(false)
                } catch (e: Exception) {
                    logger.warn("拖放打开文件失败", e)
                    dtde.dropComplete(false)
                }
            }
        })
    }

    // 在指定组件上安装文件拖放监听，确保无论鼠标落在何处都能打开文件
    private fun installFileDropTarget(component: java.awt.Component) {
        try {
            DropTarget(component, object : DropTargetListener {
                override fun dragEnter(dtde: DropTargetDragEvent) {
                    if (isDragAcceptable(dtde)) dtde.acceptDrag(DnDConstants.ACTION_COPY) else dtde.rejectDrag()
                }

                override fun dragOver(dtde: DropTargetDragEvent) {
                    if (isDragAcceptable(dtde)) dtde.acceptDrag(DnDConstants.ACTION_COPY) else dtde.rejectDrag()
                }

                override fun dropActionChanged(dtde: DropTargetDragEvent) {
                    if (isDragAcceptable(dtde)) dtde.acceptDrag(DnDConstants.ACTION_COPY) else dtde.rejectDrag()
                }

                override fun dragExit(dtde: DropTargetEvent) {}
                override fun drop(dtde: DropTargetDropEvent) {
                    if (!isDragAcceptable(dtde)) {
                        dtde.rejectDrop(); return
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    try {
                        val transferable = dtde.transferable
                        val dataFlavors = transferable.transferDataFlavors
                        for (flavor in dataFlavors) {
                            if (flavor.isFlavorJavaFileListType) {
                                @Suppress("UNCHECKED_CAST")
                                val fileList = transferable.getTransferData(flavor) as List<File>
                                fileList.filter { it.isFile && it.canRead() }.forEach { openFile(it) }
                                dtde.dropComplete(true)
                                return
                            }
                        }
                        dtde.dropComplete(false)
                    } catch (e: Exception) {
                        logger.warn("拖放打开文件失败", e); dtde.dropComplete(false)
                    }
                }
            })
        } catch (_: Exception) {
            // 忽略个别组件不支持安装 DropTarget 的情况
        }
    }

    private fun isDragAcceptable(event: DropTargetDragEvent): Boolean {
        val transferable = event.transferable
        val dataFlavors = transferable.transferDataFlavors

        for (dataFlavor in dataFlavors) {
            if (dataFlavor.isFlavorJavaFileListType) {
                return true
            }
        }
        return false
    }

    private fun isDragAcceptable(event: DropTargetDropEvent): Boolean {
        val transferable = event.transferable
        val dataFlavors = transferable.transferDataFlavors

        for (dataFlavor in dataFlavors) {
            if (dataFlavor.isFlavorJavaFileListType) {
                return true
            }
        }
        return false
    }

    private fun getCurrentTextArea(): TextArea? {
        val currentIndex = tabbedPane.selectedIndex
        return if (currentIndex >= 0) tabTextAreas[currentIndex] else null
    }

    /**
     * 格式化当前文件
     */
    private fun formatCurrentFile() {
        val file = getCurrentFile() ?: return
        val textArea = getCurrentTextArea() ?: return

        // 检查文件是否可编辑（非只读）
        if (!textArea.isEditable) {
            JOptionPane.showMessageDialog(
                this,
                "当前文件为只读，无法格式化",
                "格式化失败",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        // 获取格式化器
        val formatter = FormatterRegistry.getFormatter(file)
        if (formatter == null) {
            JOptionPane.showMessageDialog(
                this,
                "当前文件类型不支持格式化",
                "格式化失败",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        try {
            // 获取当前内容
            val currentContent = textArea.text

            // 格式化
            val formattedContent = formatter.format(currentContent)

            // 如果内容有变化，更新文本区域
            if (formattedContent != currentContent) {
                val caretPos = textArea.caretPosition
                val currentIndex = tabbedPane.selectedIndex
                
                textArea.putClientProperty("suppressDirty", true)
                
                // 使用 replaceRange 替换整个文本，这样可以保持撤销历史
                textArea.replaceRange(formattedContent, 0, currentContent.length)
                
                textArea.putClientProperty("suppressDirty", false)

                // 手动更新脏标记（因为 suppressDirty 阻止了 DocumentListener 的更新）
                if (currentIndex >= 0) {
                    val original = originalTextByIndex[currentIndex]
                    val isDirty = original != formattedContent
                    if (isDirty) {
                        dirtyTabs.add(currentIndex)
                    } else {
                        dirtyTabs.remove(currentIndex)
                    }
                    updateTabTitle(currentIndex)
                    updateTabHeaderStyles()
                }

                // 尝试恢复光标位置（如果可能）
                val newCaretPos = minOf(caretPos, formattedContent.length)
                textArea.caretPosition = newCaretPos
            }
        } catch (e: Exception) {
            logger.error("格式化文件失败: ${file.name}", e)
            JOptionPane.showMessageDialog(
                this,
                "格式化失败: ${e.message}",
                "格式化错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * 显示文本区域的右键菜单
     */
    private fun showTextAreaContextMenu(textArea: TextArea, x: Int, y: Int) {
        val menu = JPopupMenu()
        
        // 检查是否有格式化器可用
        val file = getCurrentFile()
        val hasFormatter = file != null && FormatterRegistry.getFormatter(file) != null

        menu.add(JMenuItem("查找...").apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
            addActionListener { showFindBar() }
        })
        menu.add(JMenuItem("替换...").apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_R, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
            addActionListener { showReplaceBar() }
        })
        
        if (hasFormatter) {
            menu.addSeparator()

            // 格式化快捷键：Option+Command+L (macOS) 或 Alt+Ctrl+L (其他系统)
            val formatShortcutMask = if (System.getProperty("os.name").lowercase().contains("mac")) {
                InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK
            } else {
                InputEvent.ALT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK
            }
            menu.add(JMenuItem("格式化文件").apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_L, formatShortcutMask)
                addActionListener { formatCurrentFile() }
            })
        }
        
        menu.show(textArea, x, y)
    }
    
    // 检测并更新 AndroidManifest 底部视图标签
    private fun updateManifestViewTabs(file: File?) {
        val isManifest = file?.name?.equals("AndroidManifest.xml", ignoreCase = true) == true
        
        if (isManifest && file != null && file.exists()) {
            // 显示底部视图标签
            if (!isManifestMode) {
                createManifestViewTabs(file)
            }
        } else {
            // 隐藏底部视图标签
            if (isManifestMode) {
                removeManifestViewTabs()
            }
        }
    }
    
    // 创建 AndroidManifest 底部视图标签
    private fun createManifestViewTabs(file: File) {
        try {
            // 保存文件引用和原始内容
            manifestFile = file
            val content = Files.readString(file.toPath())
            manifestOriginalContent = content
            
            // 解析 XML 内容
            val manifestData = parseAndroidManifest(content)
            
            // 创建底部标签面板（简洁样式）
            manifestViewTabs = JTabbedPane().apply {
                tabPlacement = JTabbedPane.TOP
                tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
                border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color(220, 220, 220))
                background = Color(250, 250, 250)
                preferredSize = Dimension(0, 36)
                maximumSize = Dimension(Int.MAX_VALUE, 36)
                
                // 设置简洁的标签页样式
                setUI(object : javax.swing.plaf.basic.BasicTabbedPaneUI() {
                    override fun installDefaults() {
                        super.installDefaults()
                        tabAreaInsets = java.awt.Insets(2, 4, 2, 4)
                        tabInsets = java.awt.Insets(6, 12, 6, 12)
                        selectedTabPadInsets = java.awt.Insets(0, 0, 0, 0)
                        contentBorderInsets = java.awt.Insets(0, 0, 0, 0)
                    }
                    
                    override fun paintTabBackground(g: Graphics, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean) {
                        val g2d = g.create() as Graphics2D
                        try {
                            if (isSelected) {
                                // 选中状态：白色背景
                                g2d.color = Color.WHITE
                                g2d.fillRect(x, y, w, h)
                                
                                // 蓝色底部边框
                                g2d.color = Color(0, 120, 212)
                                g2d.fillRect(x, y + h - 2, w, 2)
                            } else {
                                // 未选中状态：透明背景
                                g2d.color = Color(0, 0, 0, 0)
                                g2d.fillRect(x, y, w, h)
                            }
                        } finally {
                            g2d.dispose()
                        }
                    }
                    
                    override fun paintTabBorder(g: Graphics?, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean) {
                        // 不绘制边框
                    }
                    
                    override fun paintContentBorder(g: Graphics?, tabPlacement: Int, selectedIndex: Int) {
                        // 不绘制内容边框
                    }
                    
                    override fun paintFocusIndicator(g: Graphics?, tabPlacement: Int, rects: Array<out Rectangle>?, tabIndex: Int, iconRect: Rectangle?, textRect: Rectangle?, isSelected: Boolean) {
                        // 不绘制焦点指示器
                    }
                })
                
                font = Font("Dialog", Font.PLAIN, 12)
                
                // 监听标签切换事件
                addChangeListener {
                    val selectedIndex = selectedIndex
                    if (selectedIndex >= 0) {
                        switchManifestView(selectedIndex, manifestData)
                    }
                }
            }
            
            // 添加"全部内容"标签（显示完整源码）
            manifestViewTabs!!.addTab("全部内容", JPanel())
            
            // 添加权限标签 - 只有当存在权限时才显示
            if (manifestData.permissionsXml.isNotEmpty()) {
                manifestViewTabs!!.addTab("Permission (${manifestData.permissionsXml.size})", JPanel())
            }
            
            // 添加 Activity 标签 - 只有当存在Activity时才显示
            if (manifestData.activitiesXml.isNotEmpty()) {
                manifestViewTabs!!.addTab("Activity (${manifestData.activitiesXml.size})", JPanel())
            }
            
            // 添加 Service 标签 - 只有当存在Service时才显示
            if (manifestData.servicesXml.isNotEmpty()) {
                manifestViewTabs!!.addTab("Service (${manifestData.servicesXml.size})", JPanel())
            }
            
            // 添加 Receiver 标签 - 只有当存在Receiver时才显示
            if (manifestData.receiversXml.isNotEmpty()) {
                manifestViewTabs!!.addTab("Receiver (${manifestData.receiversXml.size})", JPanel())
            }
            
            // 添加 Provider 标签 - 只有当存在Provider时才显示
            if (manifestData.providersXml.isNotEmpty()) {
                manifestViewTabs!!.addTab("Provider (${manifestData.providersXml.size})", JPanel())
            }
            
            // 将底部标签面板添加到统一容器中，避免与查找条冲突
            // 如果已有 smaliViewTabs，需要处理布局
            if (smaliViewTabs != null) {
                // 检查是否已经有容器
                val existingContainer = if (bottomContainer.componentCount > 0) {
                    bottomContainer.getComponent(0) as? JPanel
                } else {
                    null
                }
                if (existingContainer != null && existingContainer.layout is BorderLayout) {
                    // 已有容器，直接添加到容器中
                    existingContainer.add(manifestViewTabs!!, BorderLayout.NORTH)
                } else {
                    // 创建新容器
                    val container = JPanel(BorderLayout()).apply {
                        isOpaque = false
                    }
                    container.add(manifestViewTabs!!, BorderLayout.NORTH)
                    container.add(smaliViewTabs!!, BorderLayout.SOUTH)
                    bottomContainer.removeAll()
                    bottomContainer.add(container, BorderLayout.SOUTH)
                }
            } else {
                // 检查是否已有容器
                val existingContainer = if (bottomContainer.componentCount > 0) {
                    bottomContainer.getComponent(0) as? JPanel
                } else {
                    null
                }
                if (existingContainer != null && existingContainer.layout is BorderLayout) {
                    existingContainer.add(manifestViewTabs!!, BorderLayout.NORTH)
                } else {
                    bottomContainer.add(manifestViewTabs!!, BorderLayout.SOUTH)
                }
            }
            
            isManifestMode = true
            revalidate()
            repaint()
            
        } catch (e: Exception) {
            logger.warn("构建 AndroidManifest 视图失败", e)
        }
    }
    
    // 切换 AndroidManifest 视图
    private fun switchManifestView(tabIndex: Int, manifestData: ManifestData) {
        // 获取当前 AndroidManifest.xml 文件的编辑器索引
        val fileIndex = manifestFile?.let { fileToTab[it] } ?: return
        val textArea = tabTextAreas[fileIndex] ?: return
        
        // 暂时禁用脏检测
        textArea.putClientProperty("suppressDirty", true)
        
        try {
            when (manifestViewTabs!!.getTitleAt(tabIndex)) {
                "全部内容" -> {
                    // 显示完整源码
                    textArea.text = manifestOriginalContent ?: ""
                    textArea.isEditable = true
                    textArea.syntaxEditingStyle = org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_XML
                }
                else -> {
                    // 获取标签标题以确定显示哪个部分
                    val title = manifestViewTabs!!.getTitleAt(tabIndex)
                    val content = when {
                        title.startsWith("Permission") -> manifestData.permissionsXml.joinToString("\n")
                        title.startsWith("Activity") -> manifestData.activitiesXml.joinToString("\n\n")
                        title.startsWith("Service") -> manifestData.servicesXml.joinToString("\n\n")
                        title.startsWith("Receiver") -> manifestData.receiversXml.joinToString("\n\n")
                        title.startsWith("Provider") -> manifestData.providersXml.joinToString("\n\n")
                        else -> ""
                    }
                    
                    textArea.text = content
                    textArea.isEditable = false
                    textArea.syntaxEditingStyle = org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_XML
                }
            }
            
            // 滚动到顶部
            SwingUtilities.invokeLater {
                textArea.caretPosition = 0
                textArea.scrollRectToVisible(Rectangle(0, 0, 1, 1))
            }
            
        } finally {
            // 恢复脏检测
            textArea.putClientProperty("suppressDirty", false)
        }
    }
    
    // 移除 AndroidManifest 底部视图标签
    private fun removeManifestViewTabs() {
        if (manifestViewTabs != null) {
            // 移除底部标签面板
            val container = if (bottomContainer.componentCount > 0) {
                bottomContainer.getComponent(0) as? JPanel
            } else {
                null
            }
            if (container != null && container.layout is BorderLayout) {
                // 从容器中移除
                container.remove(manifestViewTabs!!)
                if (container.componentCount == 0) {
                    // 容器为空，移除容器
                    bottomContainer.remove(container)
                } else {
                    // 容器还有其他组件（如 smali tabs），保留容器
                    bottomContainer.revalidate()
                    bottomContainer.repaint()
                }
            } else {
                // 直接添加到 bottomContainer
                bottomContainer.remove(manifestViewTabs!!)
            }
            manifestViewTabs = null
            isManifestMode = false
            manifestFile = null
            manifestOriginalContent = null
            revalidate()
            repaint()
        }
    }
    
    // 解析 AndroidManifest.xml 内容，提取原始XML片段
    private fun parseAndroidManifest(content: String): ManifestData {
        val permissionsXml = mutableListOf<String>()
        val activitiesXml = mutableListOf<String>()
        val servicesXml = mutableListOf<String>()
        val receiversXml = mutableListOf<String>()
        val providersXml = mutableListOf<String>()
        
        try {
            // 提取权限 XML
            val permissionPattern = """<uses-permission[^>]*/>""".toRegex()
            permissionsXml.addAll(permissionPattern.findAll(content).map { it.value })
            
            // 提取 Activity XML（完整的activity块，包括子元素）
            extractComponentXml(content, "activity", activitiesXml)
            
            // 提取 Service XML（完整的service块，包括子元素）
            extractComponentXml(content, "service", servicesXml)
            
            // 提取 Receiver XML（完整的receiver块，包括子元素）
            extractComponentXml(content, "receiver", receiversXml)
            
            // 提取 Provider XML（完整的provider块，包括子元素）
            extractComponentXml(content, "provider", providersXml)
            
        } catch (e: Exception) {
            logger.warn("解析 AndroidManifest 失败", e)
        }
        
        return ManifestData(permissionsXml, activitiesXml, servicesXml, receiversXml, providersXml)
    }
    
    // 提取指定组件的XML（统一处理activity、service、provider）
    private fun extractComponentXml(content: String, componentName: String, resultList: MutableList<String>) {
        // 首先查找所有自闭合标签
        val selfClosingPattern = """<$componentName[^>]*/\s*>""".toRegex()
        val selfClosingMatches = selfClosingPattern.findAll(content).toList()
        
        // 然后查找所有开始标签
        val startPattern = """<$componentName\s+[^>]*>""".toRegex()
        val startMatches = startPattern.findAll(content).toList()
        
        // 处理每个开始标签
        for (match in startMatches) {
            val startPos = match.range.first
            
            // 检查这是否是自闭合标签（避免重复）
            val isSelfClosing = selfClosingMatches.any { it.range.first == startPos }
            if (isSelfClosing) {
                resultList.add(match.value)
                continue
            }
            
            // 查找对应的结束标签
            val endTag = "</$componentName>"
            var endPos = startPos + match.value.length
            var depth = 1
            var searchPos = endPos
            
            // 使用嵌套深度计数来处理嵌套标签
            while (depth > 0 && searchPos < content.length) {
                val nextStart = content.indexOf("<$componentName", searchPos)
                val nextEnd = content.indexOf(endTag, searchPos)
                
                when {
                    nextEnd == -1 -> break // 没找到结束标签
                    nextStart != -1 && nextStart < nextEnd -> {
                        // 又找到一个开始标签，增加深度
                        depth++
                        searchPos = nextStart + componentName.length + 1
                    }
                    else -> {
                        // 找到结束标签
                        depth--
                        if (depth == 0) {
                            endPos = nextEnd + endTag.length
                        }
                        searchPos = nextEnd + endTag.length
                    }
                }
            }
            
            if (depth == 0) {
                val componentXml = content.substring(startPos, endPos)
                resultList.add(formatXml(componentXml))
            }
        }
    }
    
    // 格式化XML，重新计算缩进
    private fun formatXml(xml: String): String {
        val lines = xml.lines()
        if (lines.isEmpty()) return xml
        
        // 找到所有非空行的最小缩进量（公共前导空格数）
        val minIndent = lines
            .filter { it.isNotBlank() }
            .map { line -> line.takeWhile { it == ' ' }.length }
            .minOrNull() ?: 0
        
        // 移除公共缩进
        val unindentedLines = lines.map { line ->
            when {
                line.isBlank() -> ""
                line.length >= minIndent -> line.substring(minIndent)
                else -> line.trimStart()
            }
        }
        
        // 重新计算缩进：根标签从第0列开始，子元素每级缩进4个空格
        val result = mutableListOf<String>()
        var indentLevel = 0
        
        for (line in unindentedLines) {
            if (line.isBlank()) {
                result.add("")
                continue
            }
            
            val trimmedLine = line.trim()
            
            // 如果是结束标签，先减少缩进级别再添加
            if (trimmedLine.startsWith("</")) {
                indentLevel = maxOf(0, indentLevel - 1)
            }
            
            // 添加当前行
            result.add("    ".repeat(indentLevel) + trimmedLine)
            
            // 如果是开始标签且不是自闭合标签，增加缩进级别
            if (trimmedLine.startsWith("<") && !trimmedLine.startsWith("</") && !trimmedLine.endsWith("/>")) {
                indentLevel++
            }
        }
        
        return result.joinToString("\n")
    }
    
    private data class ManifestData(
        val permissionsXml: List<String>,
        val activitiesXml: List<String>,
        val servicesXml: List<String>,
        val receiversXml: List<String>,
        val providersXml: List<String>
    )

    private fun runOnEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }

    private fun showSmaliLoadingIndicator() {
        runOnEdt {
            if (!smaliProgressVisible) {
                mainWindow.statusBar.showProgress(
                    message = SMALI_PROGRESS_MESSAGE,
                    indeterminate = true,
                    cancellable = false
                )
                smaliProgressVisible = true
            }
        }
    }

    private fun hideSmaliLoadingIndicatorIfIdle() {
        runOnEdt {
            if (smaliProgressVisible && smaliConversionTasks.isEmpty()) {
                mainWindow.statusBar.hideProgress()
                smaliProgressVisible = false
            }
        }
    }

    private fun cancelSmaliConversion(file: File) {
        val future = smaliConversionTasks.remove(file)
        future?.cancel(true)
        hideSmaliLoadingOverlay(file)
        hideSmaliLoadingIndicatorIfIdle()
    }

    private fun getCachedJavaIfFresh(smaliFile: File): String? {
        val cached = smaliJavaContentCache[smaliFile] ?: return null
        val lm = runCatching { smaliFile.lastModified() }.getOrDefault(0L)
        val len = runCatching { smaliFile.length() }.getOrDefault(-1L)
        return if (cached.sourceLastModified == lm && cached.sourceLength == len) cached.javaContent else null
    }

    private fun getFindBar(file: File): FindReplaceBar {
        return findReplaceBars.getOrPut(file) {
            FindReplaceBar {
                fileToTab[file]?.let { index -> tabTextAreas[index] }
            }
        }
    }

    private fun attachFindBar(file: File, bar: FindReplaceBar) {
        val index = fileToTab[file] ?: return
        val tabContent = tabbedPane.getComponentAt(index) as? TabContent ?: return
        val topSlot = tabContent.topSlot
        val parent = bar.parent as? Container
        if (parent !== topSlot) {
            parent?.remove(bar)
            topSlot.add(bar, BorderLayout.CENTER)
        }
        topSlot.revalidate()
        topSlot.repaint()
    }

    private fun showSmaliLoadingOverlay(file: File) {
        val index = fileToTab[file] ?: return
        val tabContent = tabbedPane.getComponentAt(index) as? TabContent ?: return
        var panel = smaliLoadingPanels[file]
        if (panel == null) {
            val progressBar = JProgressBar().apply {
                isOpaque = false
                isIndeterminate = true
                border = BorderFactory.createEmptyBorder()
                preferredSize = Dimension(160, 6)
                maximumSize = Dimension(Int.MAX_VALUE, 6)
                minimumSize = Dimension(80, 6)
            }
            panel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
                add(progressBar, BorderLayout.CENTER)
            }
            smaliLoadingPanels[file] = panel
        }

        val topSlot = tabContent.topSlot
        if (panel.parent != topSlot) {
            topSlot.add(panel, BorderLayout.NORTH)
            topSlot.revalidate()
            topSlot.repaint()
        }
        panel.isVisible = true
    }

    private fun hideSmaliLoadingOverlay(file: File) {
        val panel = smaliLoadingPanels[file] ?: return
        val parent = panel.parent as? Container ?: return
        parent.remove(panel)
        parent.revalidate()
        parent.repaint()
        smaliLoadingPanels.remove(file)
    }
    
    // 检测并更新 Smali 底部视图标签
    private fun updateSmaliViewTabs(file: File?) {
        val isSmali = file?.let { 
            val name = it.name.lowercase()
            name.endsWith(".smali", ignoreCase = true)
        } ?: false
        
        if (isSmali && file != null && file.exists()) {
            // 显示底部视图标签
            if (!isSmaliMode) {
                logger.debug("检测到 smali 文件: ${file.name}, 创建底部 tab")
                createSmaliViewTabs(file)
            } else {
                // 如果已经是 smali 模式，但文件不同，需要更新
                if (smaliFile != file) {
                    // 先保存当前文件的 tab 状态
                    smaliFile?.let { currentFile ->
                        smaliViewTabs?.let { tabs ->
                            smaliTabState[currentFile] = tabs.selectedIndex
                            logger.debug("保存 smali 文件 ${currentFile.name} 的 tab 状态: ${tabs.selectedIndex}")
                        }
                    }
                    logger.debug("切换到新的 smali 文件: ${file.name}")
                    removeSmaliViewTabs()
                    createSmaliViewTabs(file)
                }
            }
        } else {
            // 隐藏底部视图标签
            if (isSmaliMode) {
                // 保存当前文件的 tab 状态
                smaliFile?.let { currentFile ->
                    smaliViewTabs?.let { tabs ->
                        smaliTabState[currentFile] = tabs.selectedIndex
                        logger.debug("保存 smali 文件 ${currentFile.name} 的 tab 状态: ${tabs.selectedIndex}")
                    }
                }
                logger.debug("隐藏 smali 底部 tab")
                removeSmaliViewTabs()
            }
        }
    }
    
    // 创建 Smali 底部视图标签
    private fun createSmaliViewTabs(file: File) {
        try {
            // 读取并保存上一次的视图状态（每个文件独立）
            val savedTabIndex = smaliTabState[file] ?: 0

            // 保存文件引用和原始内容
            smaliFile = file
            val content = Files.readString(file.toPath())
            smaliOriginalContent = content
            
            // 创建底部标签面板（参考 jadx 样式）
            smaliViewTabs = JTabbedPane().apply {
                tabPlacement = JTabbedPane.TOP
                tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
                border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color(220, 220, 220))
                background = Color(250, 250, 250)
                preferredSize = Dimension(0, 40)
                minimumSize = Dimension(0, 40)
                maximumSize = Dimension(Int.MAX_VALUE, 40)
                isVisible = true
                isOpaque = true
                
                // 设置简洁的标签页样式（参考 jadx）
                setUI(object : javax.swing.plaf.basic.BasicTabbedPaneUI() {
                    override fun installDefaults() {
                        super.installDefaults()
                        tabAreaInsets = java.awt.Insets(2, 4, 2, 4)
                        tabInsets = java.awt.Insets(6, 12, 6, 12)
                        selectedTabPadInsets = java.awt.Insets(0, 0, 0, 0)
                        contentBorderInsets = java.awt.Insets(0, 0, 0, 0)
                    }
                    
                    override fun paintTabBackground(g: Graphics, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean) {
                        val g2d = g.create() as Graphics2D
                        try {
                            if (isSelected) {
                                // 选中状态：白色背景
                                g2d.color = Color.WHITE
                                g2d.fillRect(x, y, w, h)
                                
                                // 蓝色底部边框
                                g2d.color = Color(0, 120, 212)
                                g2d.fillRect(x, y + h - 2, w, 2)
                            } else {
                                // 未选中状态：透明背景
                                g2d.color = Color(0, 0, 0, 0)
                                g2d.fillRect(x, y, w, h)
                            }
                        } finally {
                            g2d.dispose()
                        }
                    }
                    
                    override fun paintTabBorder(g: Graphics?, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean) {
                        // 不绘制边框
                    }
                    
                    override fun paintContentBorder(g: Graphics?, tabPlacement: Int, selectedIndex: Int) {
                        // 不绘制内容边框
                    }
                    
                    override fun paintFocusIndicator(g: Graphics?, tabPlacement: Int, rects: Array<out Rectangle>?, tabIndex: Int, iconRect: Rectangle?, textRect: Rectangle?, isSelected: Boolean) {
                        // 不绘制焦点指示器
                    }
                })
                
                font = Font("Dialog", Font.PLAIN, 12)
            }
            
            // 添加 "Smali" 标签（默认选中）
            smaliViewTabs!!.addTab(SMALI_TAB_TITLE, JPanel())
            
            // 添加 "Code" 标签
            smaliViewTabs!!.addTab(CODE_TAB_TITLE, JPanel())
            
            // 恢复之前保存的 tab 状态，如果没有则默认选中 Smali (0)
            val safeTabIndex = savedTabIndex.coerceIn(0, smaliViewTabs!!.tabCount - 1)
            smaliViewTabs!!.selectedIndex = safeTabIndex

            // 监听标签切换事件（放在 addTab + restore 之后，避免创建过程触发 change 导致状态被覆盖）
            smaliViewTabs!!.addChangeListener {
                val selected = smaliViewTabs?.selectedIndex ?: return@addChangeListener
                if (selected >= 0 && smaliFile != null && smaliFile == file) {
                    switchSmaliView(selected)
                }
            }

            // 初始化内容（因为监听器在 restore 之后才安装）
            switchSmaliView(safeTabIndex)
            
            // 将底部标签面板添加到统一容器中，避免与查找条冲突
            // 如果已有 manifestViewTabs，需要处理布局
            if (manifestViewTabs != null) {
                // 检查是否已经有容器
                val existingContainer = if (bottomContainer.componentCount > 0) {
                    bottomContainer.getComponent(0) as? JPanel
                } else {
                    null
                }
                if (existingContainer != null && existingContainer.layout is BorderLayout) {
                    // 已有容器，直接添加到容器中
                    existingContainer.add(smaliViewTabs!!, BorderLayout.SOUTH)
                } else {
                    // 创建新容器
                    val container = JPanel(BorderLayout()).apply {
                        isOpaque = false
                    }
                    container.add(manifestViewTabs!!, BorderLayout.NORTH)
                    container.add(smaliViewTabs!!, BorderLayout.SOUTH)
                    bottomContainer.removeAll()
                    bottomContainer.add(container, BorderLayout.SOUTH)
                }
            } else {
                // 没有 manifest tabs，直接添加到 bottomContainer
                // 检查是否已有容器
                val existingContainer = if (bottomContainer.componentCount > 0) {
                    bottomContainer.getComponent(0) as? JPanel
                } else {
                    null
                }
                
                if (existingContainer != null && existingContainer.layout is BorderLayout) {
                    // 已有容器，添加到容器中
                    existingContainer.add(smaliViewTabs!!, BorderLayout.SOUTH)
                } else {
                    // 没有容器，直接添加到 bottomContainer
                    bottomContainer.removeAll()
                    bottomContainer.add(smaliViewTabs!!, BorderLayout.SOUTH)
                }
            }
            
            isSmaliMode = true
            bottomContainer.revalidate()
            bottomContainer.repaint()
            revalidate()
            repaint()
            
            logger.debug("Smali 底部 tab 已创建并添加到界面")
            
        } catch (e: Exception) {
            logger.warn("构建 Smali 视图失败", e)
        }
    }
    
    // 切换 Smali 视图
    private fun switchSmaliView(tabIndex: Int) {
        // 获取当前 smali 文件的编辑器索引
        val fileIndex = smaliFile?.let { fileToTab[it] } ?: return
        val textArea = tabTextAreas[fileIndex] ?: return
        val currentFile = smaliFile ?: return

        // 保存当前选择的 tab 状态（用于在切换文件后恢复）
        smaliTabState[currentFile] = tabIndex
        logger.debug("保存 smali 文件 ${currentFile.name} 的 tab 状态: $tabIndex")

        when (smaliViewTabs!!.getTitleAt(tabIndex)) {
            SMALI_TAB_TITLE -> {
                cancelSmaliConversion(currentFile)
                textArea.putClientProperty("suppressDirty", true)
                textArea.text = smaliOriginalContent ?: ""
                textArea.isEditable = true
                textArea.detectSyntax(currentFile)
                textArea.putClientProperty("suppressDirty", false)
                hideSmaliLoadingIndicatorIfIdle()
                SwingUtilities.invokeLater {
                    textArea.caretPosition = 0
                    textArea.scrollRectToVisible(Rectangle(0, 0, 1, 1))
                }
            }
            CODE_TAB_TITLE -> {
                textArea.putClientProperty("suppressDirty", true)
                textArea.isEditable = false
                val cached = getCachedJavaIfFresh(currentFile)
                if (cached != null) {
                    textArea.text = cached
                    textArea.syntaxEditingStyle = org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA
                    textArea.putClientProperty("suppressDirty", false)
                    hideSmaliLoadingIndicatorIfIdle()
                    SwingUtilities.invokeLater {
                        textArea.caretPosition = 0
                        textArea.scrollRectToVisible(Rectangle(0, 0, 1, 1))
                    }
                } else {
                    textArea.syntaxEditingStyle = org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE
                    textArea.text = ""
                    textArea.putClientProperty("suppressDirty", false)
                    showSmaliLoadingIndicator()
                    showSmaliLoadingOverlay(currentFile)
                    scheduleSmaliConversion(currentFile, tabIndex, textArea)
                }
            }
        }
    }
    
    private fun scheduleSmaliConversion(
        file: File,
        expectedTabIndex: Int,
        fallbackTextArea: TextArea
    ) {
        smaliConversionTasks.remove(file)?.cancel(true)

        val future = smaliExecutor.submit {
            try {
                val javaContent = getJavaCodeForSmali(file)
                runOnEdt {
                    smaliConversionTasks.remove(file)
                    hideSmaliLoadingOverlay(file)
                    val stillCurrentFile = smaliFile == file
                    val currentTabs = smaliViewTabs
                    val shouldUpdateTextArea = stillCurrentFile &&
                        currentTabs != null &&
                        currentTabs.selectedIndex == expectedTabIndex &&
                        currentTabs.getTitleAt(expectedTabIndex) == CODE_TAB_TITLE

                    if (shouldUpdateTextArea) {
                        val idx = fileToTab[file]
                        val activeTextArea = idx?.let { tabTextAreas[it] } ?: fallbackTextArea
                        activeTextArea.putClientProperty("suppressDirty", true)
                        activeTextArea.text = javaContent
                        activeTextArea.syntaxEditingStyle = org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA
                        activeTextArea.isEditable = false
                        activeTextArea.putClientProperty("suppressDirty", false)
                        SwingUtilities.invokeLater {
                            activeTextArea.caretPosition = 0
                            activeTextArea.scrollRectToVisible(Rectangle(0, 0, 1, 1))
                        }
                    }
                    hideSmaliLoadingIndicatorIfIdle()
                }
            } catch (_: CancellationException) {
                runOnEdt {
                    smaliConversionTasks.remove(file)
                    hideSmaliLoadingOverlay(file)
                    hideSmaliLoadingIndicatorIfIdle()
                }
            } catch (e: Exception) {
                logger.warn("Smali 异步转换失败: ${file.name}", e)
                runOnEdt {
                    smaliConversionTasks.remove(file)
                    hideSmaliLoadingOverlay(file)
                    val stillCurrentFile = smaliFile == file
                    val currentTabs = smaliViewTabs
                    val shouldUpdateTextArea = stillCurrentFile &&
                        currentTabs != null &&
                        currentTabs.selectedIndex == expectedTabIndex &&
                        currentTabs.getTitleAt(expectedTabIndex) == CODE_TAB_TITLE

                    if (shouldUpdateTextArea) {
                        fallbackTextArea.putClientProperty("suppressDirty", true)
                        fallbackTextArea.text = "// 转换失败: ${e.message}"
                        fallbackTextArea.syntaxEditingStyle = org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE
                        fallbackTextArea.isEditable = false
                        fallbackTextArea.putClientProperty("suppressDirty", false)
                    }
                    hideSmaliLoadingIndicatorIfIdle()
                }
            }
        }

        smaliConversionTasks[file] = future
    }
    
    // 获取 Smali 文件对应的 Java 代码
    private fun getJavaCodeForSmali(smaliFile: File): String {
        logger.info("开始获取 Java 代码，smali 文件: ${smaliFile.absolutePath}")
        
        // 如果已经缓存了 Java 内容且源文件未变化，直接返回（文件级缓存）
        val cached = smaliJavaContentCache[smaliFile]
        if (cached != null) {
            val lm = runCatching { smaliFile.lastModified() }.getOrDefault(0L)
            val len = runCatching { smaliFile.length() }.getOrDefault(-1L)
            if (cached.sourceLastModified == lm && cached.sourceLength == len) {
                logger.info("使用缓存的 Java 内容（文件: ${smaliFile.name}）")
                return cached.javaContent
            }
        }
        
        try {
            // 策略1: 查找对应的 Java 文件（如果是从 JADX 反编译的 APK）
            // smali 文件路径通常类似: .../smali/com/example/MyClass.smali
            // Java 文件路径通常类似: .../java_src/com/example/MyClass.java 或 .../sources/com/example/MyClass.java
            val smaliPath = smaliFile.absolutePath
            val javaPaths = listOf(
                smaliPath.replace("/smali/", "/java_src/").replace(".smali", ".java"),
                smaliPath.replace("/smali/", "/sources/").replace(".smali", ".java"),
                smaliPath.replace("/smali_classes", "/java_src").replace(".smali", ".java"),
            )
            
            for (javaPath in javaPaths) {
                val javaFile = File(javaPath)
                if (javaFile.exists() && javaFile.canRead()) {
                    val content = Files.readString(javaFile.toPath())
                    smaliJavaContentCache[smaliFile] = SmaliJavaCacheEntry(
                        sourceLastModified = smaliFile.lastModified(),
                        sourceLength = smaliFile.length(),
                        javaContent = content
                    )
                    return content
                }
            }
            
            // 策略2: 查找同一目录下的同名 .java 文件（向上查找）
            var currentDir = smaliFile.parentFile
            while (currentDir != null) {
                val javaFile = File(currentDir, smaliFile.nameWithoutExtension + ".java")
                if (javaFile.exists() && javaFile.canRead()) {
                    val content = Files.readString(javaFile.toPath())
                    smaliJavaContentCache[smaliFile] = SmaliJavaCacheEntry(
                        sourceLastModified = smaliFile.lastModified(),
                        sourceLength = smaliFile.length(),
                        javaContent = content
                    )
                    return content
                }
                // 检查是否有 sources 或 java_src 目录
                val sourcesDir = File(currentDir, "sources")
                if (sourcesDir.exists() && sourcesDir.isDirectory) {
                    val relativePath = smaliFile.relativeTo(currentDir).path.replace("/smali/", "/").replace(".smali", ".java")
                    val javaFile2 = File(sourcesDir, relativePath)
                    if (javaFile2.exists() && javaFile2.canRead()) {
                        val content = Files.readString(javaFile2.toPath())
                        smaliJavaContentCache[smaliFile] = SmaliJavaCacheEntry(
                            sourceLastModified = smaliFile.lastModified(),
                            sourceLength = smaliFile.length(),
                            javaContent = content
                        )
                        return content
                    }
                }
                val javaSrcDir = File(currentDir, "java_src")
                if (javaSrcDir.exists() && javaSrcDir.isDirectory) {
                    val relativePath = smaliFile.relativeTo(currentDir).path.replace("/smali/", "/").replace(".smali", ".java")
                    val javaFile2 = File(javaSrcDir, relativePath)
                    if (javaFile2.exists() && javaFile2.canRead()) {
                        val content = Files.readString(javaFile2.toPath())
                        smaliJavaContentCache[smaliFile] = SmaliJavaCacheEntry(
                            sourceLastModified = smaliFile.lastModified(),
                            sourceLength = smaliFile.length(),
                            javaContent = content
                        )
                        return content
                    }
                }
                currentDir = currentDir.parentFile
            }
            
            // 策略3: 尝试使用 JADX 实时反编译（类似 MT 管理器）
            logger.info("策略3: 尝试使用 JADX 实时反编译")
            val decompiledJava = tryDecompileWithJadx(smaliFile)
            if (decompiledJava != null) {
                logger.info("实时反编译成功")
                smaliJavaContentCache[smaliFile] = SmaliJavaCacheEntry(
                    sourceLastModified = smaliFile.lastModified(),
                    sourceLength = smaliFile.length(),
                    javaContent = decompiledJava
                )
                return decompiledJava
            }
            logger.warn("实时反编译失败")
            
            // 策略4: 如果找不到，显示提示信息
            val failureMessage = """
                // 未找到对应的 Java 源码文件
                // 
                // 提示：
                // 1. 如果这是从 APK 反编译的 smali 文件，请确保已使用 JADX 反编译生成 Java 源码
                // 2. Java 源码通常位于以下目录之一：
                //    - java_src/
                //    - sources/
                //    - 与 smali 文件同目录
                // 3. 或者确保已安装 jadx 工具，可以实时反编译
                //
                // 当前 smali 文件路径: ${smaliFile.absolutePath}
            """.trimIndent()
            smaliJavaContentCache[smaliFile] = SmaliJavaCacheEntry(
                sourceLastModified = smaliFile.lastModified(),
                sourceLength = smaliFile.length(),
                javaContent = failureMessage
            )
            return failureMessage
            
        } catch (e: Exception) {
            logger.warn("获取 Java 代码失败", e)
            val errorMessage = "// 获取 Java 代码时出错: ${e.message}"
            smaliJavaContentCache[smaliFile] = SmaliJavaCacheEntry(
                sourceLastModified = smaliFile.lastModified(),
                sourceLength = smaliFile.length(),
                javaContent = errorMessage
            )
            return errorMessage
        }
    }
    
    // 尝试使用 JADX 实时反编译 smali 文件（类似 MT 管理器）
    // 策略：先将单个 smali 文件编译成 DEX，然后用 jadx 反编译
    private fun tryDecompileWithJadx(smaliFile: File): String? {
        try {
            logger.info("开始实时反编译 smali 文件: ${smaliFile.absolutePath}")
            
            // 检查 jadx 和 smali 工具是否可用
            val jadxPath = JadxTool.locate()
            if (jadxPath == null) {
                logger.warn("JADX 工具未找到，跳过实时反编译")
                return null
            }
            logger.info("找到 JADX 工具: $jadxPath")
            
            val smaliPath = SmaliTool.locate()
            if (smaliPath == null) {
                logger.warn("smali 工具未找到，尝试使用完整 DEX 文件反编译")
                // 回退到使用完整 DEX 文件的方式
                return tryDecompileWithFullDex(smaliFile)
            }
            
            logger.info("找到 smali 工具: $smaliPath，使用单个文件编译方式")
            
            // 计算 smali 文件对应的类名
            val className = extractClassNameFromSmaliPath(smaliFile.absolutePath)
            if (className == null) {
                logger.debug("无法从 smali 路径提取类名: ${smaliFile.absolutePath}")
                return null
            }
            
            // 创建临时目录
            val tempDir = Files.createTempDirectory("smali_to_java_").toFile()
            tempDir.deleteOnExit()
            
            // 步骤1: 使用 smali 工具将单个 smali 文件编译成 DEX
            val tempDexFile = File(tempDir, "classes.dex")
            logger.info("编译 smali 文件为 DEX: ${smaliFile.absolutePath} -> ${tempDexFile.absolutePath}")
            logger.info("使用 smali 工具路径: $smaliPath")
            System.out.println("=== 开始编译 smali 文件 ===")
            System.out.println("smali 工具: $smaliPath")
            System.out.println("输入文件: ${smaliFile.absolutePath}")
            System.out.println("输出文件: ${tempDexFile.absolutePath}")
            
            val assembleResult = SmaliTool.assemble(smaliFile, tempDexFile)
            
            if (assembleResult.status != SmaliTool.Status.SUCCESS) {
                logger.error("smali 编译失败 (status=${assembleResult.status}, exitCode=${assembleResult.exitCode})")
                logger.error("smali 编译错误输出: ${assembleResult.output}")
                System.err.println("=== SMALI 编译失败 ===")
                System.err.println("状态: ${assembleResult.status}")
                System.err.println("退出码: ${assembleResult.exitCode}")
                System.err.println("输出: ${assembleResult.output}")
                tempDir.deleteRecursively()
                return null
            }
            
            if (!tempDexFile.exists()) {
                logger.error("smali 编译完成但 DEX 文件不存在: ${tempDexFile.absolutePath}")
                System.err.println("=== SMALI 编译完成但文件不存在 ===")
                System.err.println("路径: ${tempDexFile.absolutePath}")
                tempDir.deleteRecursively()
                return null
            }
            
            logger.info("smali 编译成功，DEX 文件大小: ${tempDexFile.length()} 字节")
            System.out.println("✓ smali 编译成功，DEX 文件: ${tempDexFile.absolutePath} (${tempDexFile.length()} 字节)")
            
            // 步骤2: 使用 JADX 反编译 DEX 文件
            val jadxOutputDir = File(tempDir, "jadx_output")
            logger.debug("使用 JADX 反编译 DEX: ${tempDexFile.absolutePath} -> ${jadxOutputDir.absolutePath}")
            val decompileResult = JadxTool.decompile(tempDexFile, jadxOutputDir)
            
            if (decompileResult.status != JadxTool.Status.SUCCESS) {
                logger.warn("JADX 反编译失败: ${decompileResult.output}")
                tempDir.deleteRecursively()
                return null
            }
            
            // 步骤3: 查找对应的 Java 文件
            val javaRelativePath = className.replace(".", "/") + ".java"
            val possibleJavaPaths = listOf(
                File(jadxOutputDir, "sources/$javaRelativePath"),
                File(jadxOutputDir, javaRelativePath),
                File(jadxOutputDir, "src/main/java/$javaRelativePath"),
            )
            
            for (javaFile in possibleJavaPaths) {
                if (javaFile.exists() && javaFile.isFile) {
                    val content = Files.readString(javaFile.toPath())
                    tempDir.deleteRecursively()
                    logger.debug("成功使用 smali+jadx 反编译单个文件")
                    return content
                }
            }
            
            logger.debug("反编译后的 Java 文件不存在，类名: $className")
            tempDir.deleteRecursively()
            return null
            
        } catch (e: Exception) {
            logger.warn("使用 smali+jadx 实时反编译失败", e)
            return null
        }
    }
    
    // 回退方案：使用完整 DEX 文件反编译（如果 smali 工具不可用）
    private fun tryDecompileWithFullDex(smaliFile: File): String? {
        try {
            // 尝试找到对应的 DEX 文件
            var currentDir = smaliFile.parentFile
            var dexFile: File? = null
            
            while (currentDir != null) {
                val parent = currentDir.parentFile
                if (parent != null && (currentDir.name == "smali" || currentDir.name.startsWith("smali_"))) {
                    val possibleDexFiles = listOf(
                        File(parent, "classes.dex"),
                        File(parent, "classes2.dex"),
                        File(parent, "classes3.dex"),
                        File(parent, "classes4.dex"),
                        File(parent, "classes5.dex"),
                    )
                    dexFile = possibleDexFiles.firstOrNull { it.exists() && it.canRead() }
                    if (dexFile != null) break
                }
                currentDir = parent
            }
            
            if (dexFile == null || !dexFile.exists()) {
                return null
            }
            
            val className = extractClassNameFromSmaliPath(smaliFile.absolutePath) ?: return null
            
            val tempDir = Files.createTempDirectory("jadx_decompile_").toFile()
            tempDir.deleteOnExit()
            
            val result = JadxTool.decompile(dexFile, tempDir)
            if (result.status != JadxTool.Status.SUCCESS) {
                tempDir.deleteRecursively()
                return null
            }
            
            val javaRelativePath = className.replace(".", "/") + ".java"
            val javaFile = File(tempDir, "sources/$javaRelativePath")
            
            if (!javaFile.exists()) {
                val javaFile2 = File(tempDir, javaRelativePath)
                if (javaFile2.exists()) {
                    val content = Files.readString(javaFile2.toPath())
                    tempDir.deleteRecursively()
                    return content
                }
                tempDir.deleteRecursively()
                return null
            }
            
            val content = Files.readString(javaFile.toPath())
            tempDir.deleteRecursively()
            return content
            
        } catch (e: Exception) {
            logger.warn("使用完整 DEX 反编译失败", e)
            return null
        }
    }
    
    // 从 smali 文件路径提取类名
    private fun extractClassNameFromSmaliPath(smaliPath: String): String? {
        try {
            // 路径格式: .../smali/com/example/MyClass.smali
            // 或: .../smali_classes2/com/example/MyClass.smali
            val smaliPattern = """.*[/\\](?:smali(?:_classes\d+)?)[/\\](.+?)\.smali$""".toRegex()
            val match = smaliPattern.find(smaliPath) ?: return null
            val classPath = match.groupValues[1]
            return classPath.replace("/", ".").replace("\\", ".")
        } catch (e: Exception) {
            logger.warn("提取类名失败", e)
            return null
        }
    }
    
    // 移除 Smali 底部视图标签
    private fun removeSmaliViewTabs() {
        if (smaliViewTabs != null) {
            smaliFile?.let { cancelSmaliConversion(it) }
            // 移除底部标签面板
            val container = if (bottomContainer.componentCount > 0) {
                bottomContainer.getComponent(0) as? JPanel
            } else {
                null
            }
            if (container != null && container.layout is BorderLayout) {
                // 从容器中移除
                container.remove(smaliViewTabs!!)
                if (container.componentCount == 0) {
                    // 容器为空，移除容器
                    bottomContainer.remove(container)
                } else {
                    // 容器还有其他组件（如 manifest tabs），保留容器
                    bottomContainer.revalidate()
                    bottomContainer.repaint()
                }
            } else {
                // 直接添加到 bottomContainer
                bottomContainer.remove(smaliViewTabs!!)
            }
            smaliViewTabs = null
            isSmaliMode = false
            smaliFile = null
            smaliOriginalContent = null
            revalidate()
            repaint()
        }
    }
    
    // 打开压缩包文件（XAPK、AAB、AAR）
    private fun openArchiveFile(file: File) {
        try {
            // 创建压缩包浏览器视图
            val archiveView = createArchiveView(file)
            
            // 创建一个包装面板来容纳压缩包视图
            val contentPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder()
                add(archiveView, BorderLayout.CENTER)
            }
            
            val title = file.name
            tabbedPane.addTab(title, resolveTabIcon(file), contentPanel, null)
            
            val index = tabbedPane.tabCount - 1
            fileToTab[file] = index
            tabToFile[index] = file
            // 注意：压缩包文件不需要 textArea，所以不添加到 tabTextAreas
            val closeButton = createVSCodeTabHeader(file)
            tabbedPane.setTabComponentAt(index, closeButton)
            attachPopupToHeader(closeButton)
            tabbedPane.selectedIndex = index
            
            mainWindow.statusBar.setFileInfo(file.name, Files.size(file.toPath()).toString() + " B")
            mainWindow.statusBar.updateNavigation(file)
            
        } catch (e: Exception) {
            logger.error("打开压缩包文件失败", e)
            mainWindow.statusBar.showError("无法打开压缩包: ${e.message}")
        }
    }
    
    // 创建压缩包浏览器视图
    private fun createArchiveView(archiveFile: File): JPanel {
        val treeModel = DefaultTreeModel(DefaultMutableTreeNode("加载中..."))
        val fileTree = JTree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val path = getPathForLocation(e.x, e.y) ?: return
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val entry = node.userObject as? ZipEntry ?: return
                        if (!entry.isDirectory) {
                            openZipEntry(archiveFile, entry)
                        }
                    }
                }
            })
        }
        
        val infoLabel = JLabel().apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = Color.GRAY
        }
        
        val panel = JPanel(BorderLayout()).apply {
            val scrollPane = JScrollPane(fileTree).apply {
                border = BorderFactory.createEmptyBorder()
            }
            add(scrollPane, BorderLayout.CENTER)
            add(infoLabel, BorderLayout.SOUTH)
        }
        
        // 异步加载压缩包内容
        Thread {
            try {
                val zipFile = ZipFile(archiveFile)
                val rootNode = DefaultMutableTreeNode(archiveFile.name)
                val entries = zipFile.entries().toList()
                val entryMap = mutableMapOf<String, DefaultMutableTreeNode>()
                
                entries.forEach { entry ->
                    val parts = entry.name.split("/").filter { it.isNotEmpty() }
                    var currentPath = ""
                    var parentNode = rootNode
                    
                    parts.forEachIndexed { index, part ->
                        currentPath += if (currentPath.isEmpty()) part else "/$part"
                        val isDirectory = index < parts.size - 1 || entry.name.endsWith("/")
                        
                        val node = entryMap.getOrPut(currentPath) {
                            val newNode = DefaultMutableTreeNode(entry)
                            parentNode.add(newNode)
                            newNode
                        }
                        parentNode = node
                    }
                }
                
                SwingUtilities.invokeLater {
                    treeModel.setRoot(rootNode)
                    fileTree.expandRow(0)
                    infoLabel.text = "共 ${entries.size} 个文件/目录"
                }
                
                zipFile.close()
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    val errorNode = DefaultMutableTreeNode("加载失败: ${e.message}")
                    treeModel.setRoot(errorNode)
                    infoLabel.text = "无法读取压缩包"
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        
        return panel
    }
    
    // 打开压缩包中的文件条目
    private fun openZipEntry(archiveFile: File, entry: ZipEntry) {
        Thread {
            try {
                val zipFile = ZipFile(archiveFile)
                val zipEntry = zipFile.getEntry(entry.name) ?: return@Thread
                
                val inputStream = zipFile.getInputStream(zipEntry)
                val content = try {
                    inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    // 如果是二进制文件，显示提示信息
                    "// 二进制文件，无法以文本形式显示\n// 文件大小: ${zipEntry.size} 字节"
                }
                zipFile.close()
                
                // 创建临时文件并打开
                val tempFile = File.createTempFile("archive_", "_${entry.name.split("/").last()}")
                tempFile.writeText(content)
                tempFile.deleteOnExit()
                
                SwingUtilities.invokeLater {
                    openFile(tempFile)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this@Editor,
                        "无法打开文件: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}
