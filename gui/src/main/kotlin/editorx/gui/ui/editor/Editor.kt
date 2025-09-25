package editorx.gui.ui.editor

import editorx.event.ActiveFileChanged
import editorx.event.FileOpened
import editorx.event.FileSaved
import editorx.gui.ui.MainWindow
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import javax.swing.*

class Editor(private val mainWindow: MainWindow) : JPanel() {
    private val fileToTab = mutableMapOf<File, Int>()
    private val tabToFile = mutableMapOf<Int, File>()
    private val tabbedPane = JTabbedPane()
    private val tabTextAreas = mutableMapOf<Int, RSyntaxTextArea>()
    private val dirtyTabs = mutableSetOf<Int>()

    init {
        // 设置JPanel的布局
        layout = java.awt.BorderLayout()
        background = Color.WHITE

        // 配置内部的JTabbedPane
        tabbedPane.apply {
            tabPlacement = JTabbedPane.TOP
            tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT
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
        }
        
        // 设置拖放支持 - 确保整个Editor区域都支持拖放
        enableDropTarget()
        
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

    fun openFile(file: File) {
        if (fileToTab.containsKey(file)) { 
            tabbedPane.selectedIndex = fileToTab[file]!!
            return 
        }
        val textArea = RSyntaxTextArea().apply {
            syntaxEditingStyle = detectSyntax(file)
            font = Font("Consolas", Font.PLAIN, 14)
            addCaretListener {
                val caretPos = caretPosition
                val line = try { getLineOfOffset(caretPos) + 1 } catch (_: Exception) { 1 }
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
        } catch (e: Exception) {
            textArea.text = "无法读取文件: ${e.message}"
            textArea.isEditable = false
        }
        val scroll = RTextScrollPane(textArea).apply {
            border = javax.swing.BorderFactory.createEmptyBorder()
            background = Color.WHITE
            viewport.background = Color.WHITE
        }
        val title = file.name
        tabbedPane.addTab(title, null, scroll, null)
        val index = tabbedPane.tabCount - 1
        fileToTab[file] = index
        tabToFile[index] = file
        tabTextAreas[index] = textArea
        val closeButton = createCloseButton(file, index)
        tabbedPane.setTabComponentAt(index, closeButton)
        tabbedPane.selectedIndex = index
        dirtyTabs.remove(index)
    }

    private fun createCloseButton(file: File, index: Int): JPanel = JPanel().apply {
        layout = java.awt.BorderLayout(); isOpaque = false
        val label = JLabel(file.name).apply { 
            border = BorderFactory.createEmptyBorder(0, 0, 0, 5)
            horizontalAlignment = JLabel.LEFT
        }
        add(label, java.awt.BorderLayout.CENTER)
        val closeLabel = JLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 14f)
            foreground = Color.GRAY
            preferredSize = Dimension(16, 16)
            horizontalAlignment = JLabel.CENTER
            verticalAlignment = JLabel.CENTER
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { foreground = Color.RED; cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR) }
                override fun mouseExited(e: MouseEvent) { foreground = Color.GRAY; cursor = java.awt.Cursor.getDefaultCursor() }
                override fun mouseClicked(e: MouseEvent) { closeTab(index) }
            })
        }
        add(closeLabel, java.awt.BorderLayout.EAST)
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
            val newTabTextAreas = mutableMapOf<Int, RSyntaxTextArea>()
            for (i in 0 until tabbedPane.tabCount) {
                val f = tabToFile[i]
                if (f != null) { newTabToFile[i] = f; newFileToTab[f] = i }
                tabTextAreas[i]?.let { newTabTextAreas[i] = it }
            }
            tabToFile.clear(); tabToFile.putAll(newTabToFile)
            fileToTab.clear(); fileToTab.putAll(newFileToTab)
            tabTextAreas.clear(); tabTextAreas.putAll(newTabTextAreas)
        }
    }

    private fun detectSyntax(file: File): String = when {
        file.name.endsWith(".smali") -> "text/smali"
        file.name.endsWith(".xml") -> SyntaxConstants.SYNTAX_STYLE_XML
        file.name.endsWith(".java") -> SyntaxConstants.SYNTAX_STYLE_JAVA
        file.name.endsWith(".kt") -> SyntaxConstants.SYNTAX_STYLE_KOTLIN
        file.name.endsWith(".js") -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
        file.name.endsWith(".css") -> SyntaxConstants.SYNTAX_STYLE_CSS
        file.name.endsWith(".html") -> SyntaxConstants.SYNTAX_STYLE_HTML
        file.name.endsWith(".json") -> SyntaxConstants.SYNTAX_STYLE_JSON
        else -> SyntaxConstants.SYNTAX_STYLE_NONE
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

    private fun getLineStartOffsetOfCurrentLine(area: RSyntaxTextArea): Int {
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
