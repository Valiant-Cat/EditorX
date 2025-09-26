package editorx.gui.main.editor

import editorx.event.ActiveFileChanged
import editorx.event.FileOpened
import editorx.event.FileSaved
import editorx.gui.main.MainWindow
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Color
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.dnd.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import javax.swing.*
import editorx.gui.ui.theme.ThemeManager
import editorx.syntax.SyntaxHighlighterManager

class Editor(private val mainWindow: MainWindow) : JPanel() {
    private val fileToTab = mutableMapOf<File, Int>()
    private val tabToFile = mutableMapOf<Int, File>()
    private val tabbedPane = JTabbedPane()
    private val tabTextAreas = mutableMapOf<Int, CustomSyntaxTextArea>()
    private val dirtyTabs = mutableSetOf<Int>()

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
            mainWindow.services.eventBus.publish(ActiveFileChanged(file?.absolutePath))
            updateTabHeaderStyles()
        }

        // 设置拖放支持 - 确保整个Editor区域都支持拖放
        enableDropTarget()
        // 同时在 tabbedPane 表面也安装 DropTarget，避免已有文件时事件落到子组件而丢失
        installFileDropTarget(tabbedPane)

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
            return
        }
        val textArea = CustomSyntaxTextArea().apply {
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
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = markDirty(true)
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = markDirty(true)
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = markDirty(true)
                private fun markDirty(d: Boolean) {
                    val scrollComp = this@apply.parent?.parent as? java.awt.Component ?: return
                    val index = tabbedPane.indexOfComponent(scrollComp)
                    if (index < 0) return
                    if (index >= 0) {
                        if (d) dirtyTabs.add(index) else dirtyTabs.remove(index)
                        updateTabTitle(index)
                    }
                }
            })
        }
        try {
            textArea.text = Files.readString(file.toPath())
            textArea.discardAllEdits()
            mainWindow.statusBar.setFileInfo(file.name, Files.size(file.toPath()).toString() + " B")
            mainWindow.services.workspace.addRecentFile(file)
            mainWindow.services.eventBus.publish(FileOpened(file.absolutePath))
            
            // 在文本加载完成后应用语法高亮
            javax.swing.SwingUtilities.invokeLater {
                textArea.setFile(file)
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
        tabbedPane.addTab(title, null, scroll, null)
        val index = tabbedPane.tabCount - 1
        fileToTab[file] = index
        tabToFile[index] = file
        tabTextAreas[index] = textArea
        val closeButton = createVSCodeTabHeader(file)
        tabbedPane.setTabComponentAt(index, closeButton)
        tabbedPane.selectedIndex = index
        dirtyTabs.remove(index)
        updateTabHeaderStyles()
    }

    private fun createTabHeader(file: File): JPanel = JPanel().apply {
        layout = java.awt.BorderLayout(); isOpaque = true; background = Color.WHITE
        val titleLabel = JLabel(file.name).apply {
            border = BorderFactory.createEmptyBorder(0, 8, 0, 6)
            horizontalAlignment = JLabel.LEFT
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
            val newTabTextAreas = mutableMapOf<Int, CustomSyntaxTextArea>()
            for (i in 0 until tabbedPane.tabCount) {
                val f = tabToFile[i]
                if (f != null) {
                    newTabToFile[i] = f; newFileToTab[f] = i
                }
                tabTextAreas[i]?.let { newTabTextAreas[i] = it }
            }
            tabToFile.clear(); tabToFile.putAll(newTabToFile)
            fileToTab.clear(); fileToTab.putAll(newFileToTab)
            tabTextAreas.clear(); tabTextAreas.putAll(newTabTextAreas)
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
            updateTabTitle(idx)
            mainWindow.statusBar.showSuccess("已保存: ${file.name}")
            mainWindow.services.eventBus.publish(FileSaved(file.absolutePath))
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
            mainWindow.services.workspace.addRecentFile(file)
            mainWindow.statusBar.showSuccess("已保存: ${file.name}")
            mainWindow.services.eventBus.publish(FileSaved(file.absolutePath))
        }
    }

    private fun updateTabTitle(index: Int) {
        val file = tabToFile[index]
        val base = file?.name ?: "Untitled"
        val dirty = if (dirtyTabs.contains(index)) "*" else ""
        val component = tabbedPane.getTabComponentAt(index) as? JPanel
        if (component != null) {
            val label = (component.getComponent(0) as? JLabel)
            label?.text = dirty + base
        } else {
            tabbedPane.setTitleAt(index, dirty + base)
        }
    }

    private fun getLineStartOffsetOfCurrentLine(area: CustomSyntaxTextArea): Int {
        val caretPos = area.caretPosition
        val line = area.getLineOfOffset(caretPos)
        return area.getLineStartOffset(line)
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
}
