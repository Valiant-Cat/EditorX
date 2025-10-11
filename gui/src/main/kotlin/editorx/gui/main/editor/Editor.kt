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
            mainWindow.toolBar.updateNavigation(file)

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
            mainWindow.toolBar.updateNavigation(file)
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
        mainWindow.toolBar.updateNavigation(file)
        
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
            mainWindow.toolBar.updateNavigation(getCurrentFile())
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
            // 解析 XML 内容
            val content = Files.readString(file.toPath())
            val manifestData = parseAndroidManifest(content)
            
            // 创建底部标签面板（带样式优化）
            manifestViewTabs = JTabbedPane().apply {
                tabPlacement = JTabbedPane.TOP
                tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
                border = BorderFactory.createEmptyBorder()
                background = Color.WHITE
                
                // 设置标签页样式
                setUI(object : javax.swing.plaf.basic.BasicTabbedPaneUI() {
                    override fun installDefaults() {
                        super.installDefaults()
                        tabAreaInsets = java.awt.Insets(2, 4, 0, 4)
                        tabInsets = java.awt.Insets(6, 12, 6, 12)
                        selectedTabPadInsets = java.awt.Insets(0, 0, 0, 0)
                        contentBorderInsets = java.awt.Insets(1, 0, 0, 0)
                    }
                    
                    override fun paintTabBackground(g: Graphics, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean) {
                        val g2d = g.create() as Graphics2D
                        try {
                            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                            
                            if (isSelected) {
                                // 选中标签：白色背景
                                g2d.color = Color.WHITE
                                g2d.fillRoundRect(x, y, w, h + 2, 6, 6)
                                
                                // 底部蓝色线条
                                g2d.color = Color(0, 122, 204)
                                g2d.fillRect(x, y + h - 2, w, 3)
                            } else {
                                // 未选中标签：浅灰色背景
                                g2d.color = Color(245, 245, 245)
                                g2d.fillRoundRect(x, y, w, h, 6, 6)
                            }
                        } finally {
                            g2d.dispose()
                        }
                    }
                    
                    override fun paintTabBorder(g: Graphics?, tabPlacement: Int, tabIndex: Int, x: Int, y: Int, w: Int, h: Int, isSelected: Boolean) {
                        // 不绘制边框
                    }
                    
                    override fun paintContentBorder(g: Graphics?, tabPlacement: Int, selectedIndex: Int) {
                        // 绘制一条细线作为分隔
                        if (g != null) {
                            g.color = Color(230, 230, 230)
                            g.fillRect(0, calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight) - 1, tabPane.width, 1)
                        }
                    }
                    
                    override fun paintFocusIndicator(g: Graphics?, tabPlacement: Int, rects: Array<out Rectangle>?, tabIndex: Int, iconRect: Rectangle?, textRect: Rectangle?, isSelected: Boolean) {
                        // 不绘制焦点指示器
                    }
                })
                
                font = Font("Dialog", Font.PLAIN, 13)
            }
            
            // 添加权限标签 - 只有当存在权限时才显示
            if (manifestData.permissions.isNotEmpty()) {
                val permissionsContent = manifestData.permissions.joinToString("\n") { "• $it" }
                manifestViewTabs!!.addTab("Permission (${manifestData.permissions.size})", createManifestContentArea(permissionsContent))
            }
            
            // 添加 Activity 标签 - 只有当存在Activity时才显示
            if (manifestData.activities.isNotEmpty()) {
                val activitiesContent = manifestData.activities.joinToString("\n\n") { it }
                manifestViewTabs!!.addTab("Activity (${manifestData.activities.size})", createManifestContentArea(activitiesContent))
            }
            
            // 添加 Service 标签 - 只有当存在Service时才显示
            if (manifestData.services.isNotEmpty()) {
                val servicesContent = manifestData.services.joinToString("\n\n") { it }
                manifestViewTabs!!.addTab("Service (${manifestData.services.size})", createManifestContentArea(servicesContent))
            }
            
            // 添加 Provider 标签 - 只有当存在Provider时才显示
            if (manifestData.providers.isNotEmpty()) {
                val providersContent = manifestData.providers.joinToString("\n\n") { it }
                manifestViewTabs!!.addTab("Provider (${manifestData.providers.size})", createManifestContentArea(providersContent))
            }
            
            // 使用 JSplitPane 分割主编辑器和底部视图
            val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, manifestViewTabs)
            splitPane.isOneTouchExpandable = true
            splitPane.dividerSize = 8
            
            // 移除原有的tabbedPane，添加splitPane
            remove(tabbedPane)
            add(splitPane, java.awt.BorderLayout.CENTER)
            
            // 设置分割器位置（70%给主编辑器，30%给底部视图）
            SwingUtilities.invokeLater {
                splitPane.setDividerLocation(0.7)
            }
            
            isManifestMode = true
            revalidate()
            repaint()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // 移除 AndroidManifest 底部视图标签
    private fun removeManifestViewTabs() {
        if (manifestViewTabs != null) {
            // 获取当前的分割面板
            val splitPane = getComponent(0) as? JSplitPane
            if (splitPane != null) {
                // 移除分割面板，恢复原始布局
                remove(splitPane)
                add(tabbedPane, java.awt.BorderLayout.CENTER)
                
                manifestViewTabs = null
                isManifestMode = false
                revalidate()
                repaint()
            }
        }
    }
    
    // 创建 AndroidManifest 内容区域
    private fun createManifestContentArea(content: String): JScrollPane {
        val textArea = javax.swing.JTextArea(content).apply {
            isEditable = false
            font = Font("Consolas", Font.PLAIN, 13)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            lineWrap = false
            tabSize = 4
            background = Color.WHITE
        }
        return JScrollPane(textArea).apply {
            border = BorderFactory.createEmptyBorder()
            background = Color.WHITE
            viewport.background = Color.WHITE
        }
    }
    
    // 解析 AndroidManifest.xml 内容
    private fun parseAndroidManifest(content: String): ManifestData {
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<String>()
        val services = mutableListOf<String>()
        val providers = mutableListOf<String>()
        
        try {
            // 提取权限
            val permissionPattern = """<uses-permission\s+android:name="([^"]+)"""".toRegex()
            permissions.addAll(permissionPattern.findAll(content).map { it.groupValues[1] })
            
            // 提取 Activity
            val activityPattern = """<activity[^>]*android:name="([^"]+)"[^>]*>""".toRegex()
            for (match in activityPattern.findAll(content)) {
                val name = match.groupValues[1]
                val startPos = match.range.first
                val endPos = content.indexOf("</activity>", startPos).takeIf { it > 0 } ?: (startPos + 200)
                val activityBlock = content.substring(startPos, endPos.coerceAtMost(content.length))
                
                val info = buildString {
                    append("Activity: $name\n")
                    
                    // 检查是否为 LAUNCHER
                    if (activityBlock.contains("android.intent.action.MAIN") && 
                        activityBlock.contains("android.intent.category.LAUNCHER")) {
                        append("  [LAUNCHER]\n")
                    }
                    
                    // 提取 exported
                    val exportedMatch = """android:exported="([^"]+)"""".toRegex().find(activityBlock)
                    if (exportedMatch != null) {
                        append("  exported: ${exportedMatch.groupValues[1]}\n")
                    }
                    
                    // 提取 intent-filter
                    if (activityBlock.contains("<intent-filter")) {
                        append("  含有 intent-filter\n")
                    }
                }
                activities.add(info)
            }
            
            // 提取 Service
            val servicePattern = """<service[^>]*android:name="([^"]+)"[^>]*>""".toRegex()
            for (match in servicePattern.findAll(content)) {
                val name = match.groupValues[1]
                val startPos = match.range.first
                val endPos = content.indexOf("</service>", startPos).takeIf { it > 0 } ?: (startPos + 200)
                val serviceBlock = content.substring(startPos, endPos.coerceAtMost(content.length))
                
                val info = buildString {
                    append("Service: $name\n")
                    val exportedMatch = """android:exported="([^"]+)"""".toRegex().find(serviceBlock)
                    if (exportedMatch != null) {
                        append("  exported: ${exportedMatch.groupValues[1]}\n")
                    }
                    if (serviceBlock.contains("<intent-filter")) {
                        append("  含有 intent-filter\n")
                    }
                }
                services.add(info)
            }
            
            // 提取 Provider
            val providerPattern = """<provider[^>]*android:name="([^"]+)"[^>]*>""".toRegex()
            for (match in providerPattern.findAll(content)) {
                val name = match.groupValues[1]
                val startPos = match.range.first
                val endPos = content.indexOf("</provider>", startPos).takeIf { it > 0 } ?: (startPos + 200)
                val providerBlock = content.substring(startPos, endPos.coerceAtMost(content.length))
                
                val info = buildString {
                    append("Provider: $name\n")
                    val exportedMatch = """android:exported="([^"]+)"""".toRegex().find(providerBlock)
                    if (exportedMatch != null) {
                        append("  exported: ${exportedMatch.groupValues[1]}\n")
                    }
                    val authMatch = """android:authorities="([^"]+)"""".toRegex().find(providerBlock)
                    if (authMatch != null) {
                        append("  authorities: ${authMatch.groupValues[1]}\n")
                    }
                }
                providers.add(info)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return ManifestData(permissions, activities, services, providers)
    }
    
    private data class ManifestData(
        val permissions: List<String>,
        val activities: List<String>,
        val services: List<String>,
        val providers: List<String>
    )
}
