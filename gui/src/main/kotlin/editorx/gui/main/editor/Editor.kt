package editorx.gui.main.editor

import editorx.filetype.FileTypeRegistry
import editorx.gui.core.theme.ThemeManager
import editorx.gui.main.MainWindow
import editorx.gui.main.explorer.ExplorerIcons
import editorx.util.IconUtil
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.*
import java.awt.dnd.*
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.io.File
import java.nio.file.Files
import javax.swing.*

class Editor(private val mainWindow: MainWindow) : JPanel() {
    private val fileToTab = mutableMapOf<File, Int>()
    private val tabToFile = mutableMapOf<Int, File>()
    private val tabbedPane = JTabbedPane()
    private val tabTextAreas = mutableMapOf<Int, TextArea>()
    private val dirtyTabs = mutableSetOf<Int>()
    private val originalTextByIndex = mutableMapOf<Int, String>()
    
    // AndroidManifest 底部视图
    private var manifestViewTabs: JTabbedPane? = null
    private var isManifestMode = false
    private var manifestFile: File? = null
    private var manifestOriginalContent: String? = null

    init {
        // 设置JPanel的布局
        layout = java.awt.BorderLayout()
        background = Color.WHITE

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

        // 将JTabbedPane添加到JPanel中
        add(tabbedPane, java.awt.BorderLayout.CENTER)

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
                    e.printStackTrace()
                }

                return false
            }
        }
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
                    val index = tabbedPane.indexOfComponent(scrollComp)
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
                    val index = tabbedPane.indexOfComponent(scrollComp)
                    val f = tabToFile[index] ?: return
                    attemptNavigate(f, this@apply)
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
        tabbedPane.addTab(title, resolveTabIcon(file), scroll, null)
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
            val file = tabToFile[index]
            tabbedPane.removeTabAt(index)
            file?.let { fileToTab.remove(it) }
            tabToFile.remove(index)
            tabTextAreas.remove(index)
            dirtyTabs.remove(index)
            val newTabToFile = mutableMapOf<Int, File>()
            val newFileToTab = mutableMapOf<File, Int>()
            val newTabTextAreas = mutableMapOf<Int, TextArea>()
            val newOriginal = mutableMapOf<Int, String>()
            for (i in 0 until tabbedPane.tabCount) {
                val f = tabToFile[i]
                if (f != null) {
                    newTabToFile[i] = f; newFileToTab[f] = i
                }
                tabTextAreas[i]?.let { newTabTextAreas[i] = it }
                originalTextByIndex[i]?.let { newOriginal[i] = it }
            }
            tabToFile.clear(); tabToFile.putAll(newTabToFile)
            fileToTab.clear(); fileToTab.putAll(newFileToTab)
            tabTextAreas.clear(); tabTextAreas.putAll(newTabTextAreas)
            originalTextByIndex.clear(); originalTextByIndex.putAll(newOriginal)
            mainWindow.statusBar.updateNavigation(getCurrentFile())
        }
    }

    fun getCurrentFile(): File? = if (tabbedPane.selectedIndex >= 0) tabToFile[tabbedPane.selectedIndex] else null

    fun hasUnsavedChanges(): Boolean = dirtyTabs.isNotEmpty()

    fun saveCurrent() {
        val idx = tabbedPane.selectedIndex
        if (idx < 0) return
        val file = tabToFile[idx]
        val ta = tabTextAreas[idx]
        if (ta != null && file != null && file.canWrite()) {
            runCatching { Files.writeString(file.toPath(), ta.text) }
            dirtyTabs.remove(idx)
            originalTextByIndex[idx] = ta.text
            updateTabTitle(idx)
            mainWindow.statusBar.showSuccess("已保存: ${file.name}")
        } else {
            saveCurrentAs()
        }
    }

    fun saveCurrentAs() {
        val idx = tabbedPane.selectedIndex
        if (idx < 0) return
        val ta = tabTextAreas[idx] ?: return
        val chooser = javax.swing.JFileChooser()
        if (chooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            runCatching { Files.writeString(file.toPath(), ta.text) }
            // Update mappings
            tabToFile[idx]?.let { fileToTab.remove(it) }
            tabToFile[idx] = file
            fileToTab[file] = idx
            updateTabTitle(idx)
            dirtyTabs.remove(idx)
            originalTextByIndex[idx] = ta.text
            mainWindow.guiControl.workspace.addRecentFile(file)
            mainWindow.statusBar.showSuccess("已保存: ${file.name}")
        }
    }

    private fun resolveTabIcon(file: File?): Icon? {
        val target = file ?: return ExplorerIcons.AnyType?.let { IconUtil.resizeIcon(it, 16, 16) }
        if (target.isDirectory) {
            return ExplorerIcons.Folder?.let { IconUtil.resizeIcon(it, 16, 16) }
        }
        val fileTypeIcon =
            FileTypeRegistry.getFileTypeByFileName(file.name)?.getIcon()?.let { IconUtil.resizeIcon(it, 16, 16) }
        return fileTypeIcon ?: ExplorerIcons.AnyType?.let { IconUtil.resizeIcon(it, 16, 16) }
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
                    e.printStackTrace()
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
                        e.printStackTrace(); dtde.dropComplete(false)
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
            
            // 将底部标签面板添加到主面板的南侧
            add(manifestViewTabs, java.awt.BorderLayout.SOUTH)
            
            isManifestMode = true
            revalidate()
            repaint()
            
        } catch (e: Exception) {
            e.printStackTrace()
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
            remove(manifestViewTabs)
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
            e.printStackTrace()
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
}
