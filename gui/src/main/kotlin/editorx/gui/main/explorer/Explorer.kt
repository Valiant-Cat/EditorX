package editorx.gui.main.explorer

import editorx.gui.main.MainWindow
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileSystemView
import editorx.gui.utils.IconUtil

class Explorer(private val mainWindow: MainWindow) : JPanel(BorderLayout()) {

    private val searchField = JTextField()
    private val refreshBtn = JButton("刷新")
    private val showHiddenCheck = JCheckBox("显示隐藏文件")
    private val treeRoot = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(treeRoot)
    private val tree = JTree(treeModel)

    init {
        buildUI()
        installListeners()
        installFileDropTarget()
        showEmptyRoot()
    }

    private fun buildUI() {
        // Compact toolbar-like header
        val toolBar = JToolBar().apply {
            isFloatable = false
            border = EmptyBorder(4, 4, 4, 0)
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }

        // Search field with placeholder; stretch horizontally
        searchField.columns = 16
        searchField.putClientProperty("JTextField.placeholderText", "搜索...")
        searchField.maximumSize = java.awt.Dimension(Int.MAX_VALUE, searchField.preferredSize.height)
        toolBar.add(searchField)
        toolBar.add(Box.createHorizontalGlue())

        toolBar.add(Box.createHorizontalStrut(4))
        toolBar.addSeparator()
        toolBar.add(Box.createHorizontalStrut(4))

        // Right side: options + refresh
        showHiddenCheck.text = "显示隐藏文件"
        toolBar.add(showHiddenCheck)
        refreshBtn.isFocusable = false
        refreshBtn.margin = java.awt.Insets(2, 10, 2, 10)
        toolBar.add(refreshBtn)

        add(toolBar, BorderLayout.NORTH)

        tree.isRootVisible = true
        tree.showsRootHandles = true
        // 单击只选中；双击才展开/收起。点击左侧的展开图标仍然是单击生效（JTree 默认行为）
        tree.toggleClickCount = 2
        tree.cellRenderer = FileTreeCellRenderer()
        add(JScrollPane(tree), BorderLayout.CENTER)
    }

    private fun installListeners() {
        refreshBtn.addActionListener { refreshRoot() }
        showHiddenCheck.addChangeListener { refreshRootPreserveSelection() }
        searchField.addActionListener { selectFirstMatch(searchField.text.trim()) }
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) searchField.text = ""
            }
        })

        tree.addTreeWillExpandListener(object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent) {
                val node = event.path.lastPathComponent as? FileNode ?: return
                node.loadChildrenIfNeeded(showHiddenCheck.isSelected)
            }
            override fun treeWillCollapse(event: TreeExpansionEvent) {}
        })

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = tree.getClosestRowForLocation(e.x, e.y)
                if (row == -1) return
                val bounds = tree.getRowBounds(row) ?: return
                // 仅当点击发生在该行的垂直范围内才认为命中（横向不做限制，实现整行可点击）
                if (e.y < bounds.y || e.y >= bounds.y + bounds.height) return
                val path = tree.getPathForRow(row) ?: return
                val node = path.lastPathComponent as? FileNode ?: return

                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                    // 文件：双击打开；目录：交给 JTree 自身处理展开/收起，避免重复切换
                    if (node.file.isFile) openFile(node.file)
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(node, e.x, e.y)
                }
            }
        })

        tree.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                val node = (tree.lastSelectedPathComponent as? FileNode) ?: return
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> if (node.file.isFile) openFile(node.file)
                    KeyEvent.VK_DELETE -> deleteNode(node)
                    KeyEvent.VK_BACK_SPACE -> reveal(node)
                }
            }
        })
    }

    fun refreshRoot() {
        val rootDir = mainWindow.guiControl.workspace.getWorkspaceRoot()
        if (rootDir == null || !rootDir.exists()) {
            showEmptyRoot(); return
        }
        val newRoot = FileNode(rootDir)
        newRoot.loadChildrenIfNeeded(showHiddenCheck.isSelected)
        treeModel.setRoot(newRoot)
        tree.isEnabled = true
        tree.expandPath(TreePath(treeModel.root))
        tree.selectionPath = TreePath(treeModel.root)
        tree.updateUI()
    }

    private fun showEmptyRoot() {
        val placeholder = DefaultMutableTreeNode("未打开工作区 — 将文件夹拖拽到此处")
        treeModel.setRoot(placeholder)
        tree.isEnabled = true
        tree.updateUI()
    }

    private fun refreshRootPreserveSelection() {
        val selFile = (tree.lastSelectedPathComponent as? FileNode)?.file
        refreshRoot()
        if (selFile != null) selectFile(selFile)
    }

    private fun togglePath(path: TreePath) {
        if (tree.isExpanded(path)) tree.collapsePath(path) else tree.expandPath(path)
    }

    private fun selectFirstMatch(q: String) {
        if (q.isEmpty()) return
        val root = treeModel.root as? FileNode ?: return
        val node = depthFirstSearch(root) { it.file.name.contains(q, ignoreCase = true) }
        if (node != null) {
            val tp = TreePath(node.path)
            expandTo(tp); tree.selectionPath = tp; tree.scrollPathToVisible(tp)
        }
    }

    private fun selectFile(target: File) {
        val root = treeModel.root as? FileNode ?: return
        val node = depthFirstSearch(root) { it.file == target }
        if (node != null) {
            val tp = TreePath(node.path)
            expandTo(tp); tree.selectionPath = tp; tree.scrollPathToVisible(tp)
        }
    }

    private fun expandTo(path: TreePath) {
        var p: TreePath? = path.parentPath
        while (p != null) { tree.expandPath(p); p = p.parentPath }
    }

    private fun depthFirstSearch(n: FileNode, pred: (FileNode) -> Boolean): FileNode? {
        if (pred(n)) return n
        n.loadChildrenIfNeeded(showHiddenCheck.isSelected)
        for (i in 0 until n.childCount) {
            val c = n.getChildAt(i) as FileNode
            val r = depthFirstSearch(c, pred)
            if (r != null) return r
        }
        return null
    }

    private fun openFile(file: File) {
        try {
            mainWindow.editor.openFile(file)
            mainWindow.guiControl.workspace.addRecentFile(file)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "打开文件失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun showContextMenu(node: FileNode, x: Int, y: Int) {
        val menu = JPopupMenu()
        if (node.file.isFile) {
            menu.add(JMenuItem("打开").apply { addActionListener { openFile(node.file) } })
        }
        menu.add(JMenuItem("在系统中显示").apply { addActionListener { reveal(node) } })
        menu.addSeparator()
        menu.add(JMenuItem("刷新").apply { addActionListener { refreshNode(node) } })
        menu.add(JMenuItem("删除").apply { addActionListener { deleteNode(node) } })
        menu.show(tree, x, y)
    }

    private fun reveal(node: FileNode) {
        val f = if (node.file.isDirectory) node.file else node.file.parentFile
        try {
            Desktop.getDesktop().open(f)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "无法打开系统文件管理器: ${e.message}")
        }
    }

    private fun refreshNode(node: FileNode) {
        setWait(true)
        try {
            node.reload(showHiddenCheck.isSelected)
            treeModel.nodeStructureChanged(node)
        } finally { setWait(false) }
    }

    private fun deleteNode(node: FileNode) {
        val f = node.file
        val confirm = JOptionPane.showConfirmDialog(
            this,
            if (f.isDirectory) "确定要删除该文件夹及其内容吗？\n${f.absolutePath}"
            else "确定要删除该文件吗？\n${f.absolutePath}",
            "确认删除",
            JOptionPane.OK_CANCEL_OPTION
        )
        if (confirm != JOptionPane.OK_OPTION) return

        setWait(true)
        try {
            deleteRecursively(f)
            val parent = node.parent as? FileNode
            if (parent != null) refreshNode(parent) else refreshRoot()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "删除失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        } finally { setWait(false) }
    }

    private fun deleteRecursively(f: File) {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursively(it) }
        if (!Files.deleteIfExists(f.toPath())) {
            // move to temp as fallback
            val tmp = File(f.parentFile, ".__deleted__${System.currentTimeMillis()}_${f.name}")
            Files.move(f.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.deleteIfExists(tmp.toPath())
        }
    }

    private fun setWait(wait: Boolean) {
        cursor = if (wait) Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) else Cursor.getDefaultCursor()
    }

    // Node representing a File with lazy children loading
    private class FileNode(val file: File) : DefaultMutableTreeNode(file) {
        private var loaded = false

        override fun isLeaf(): Boolean = file.isFile

        fun loadChildrenIfNeeded(showHidden: Boolean) {
            if (loaded || file.isFile) return
            loaded = true
            removeAllChildren()
            val children = file.listFiles()?.asSequence()
                ?.filter { showHidden || !it.name.startsWith(".") }
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?.toList() ?: emptyList()
            children.forEach { add(FileNode(it)) }
        }

        fun reload(showHidden: Boolean) {
            loaded = false
            loadChildrenIfNeeded(showHidden)
        }

        override fun toString(): String = file.name.ifEmpty { file.absolutePath }
    }

    /**
     * 自定义渲染：为目录和常见后缀的文件展示系统图标（带缓存）。
     */
    private class FileTreeCellRenderer : javax.swing.tree.DefaultTreeCellRenderer() {
        private val fs = FileSystemView.getFileSystemView()
        private val fileIconCache = mutableMapOf<String, Icon>()
        private val folderClosed: Icon? = UIManager.getIcon("Tree.closedIcon")
        private val folderOpen: Icon? = UIManager.getIcon("Tree.openIcon")
        private val defaultFile: Icon? = UIManager.getIcon("FileView.fileIcon")

        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): java.awt.Component {
            val c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val node = value as? DefaultMutableTreeNode
            val file = (node as? FileNode)?.file
            if (file != null) {
                icon = when {
                    file.isDirectory -> if (expanded) folderOpen ?: icon else folderClosed ?: icon
                    else -> getIconForFile(file)
                }
                text = file.name.ifEmpty { file.absolutePath }
            }
            return c
        }

        private fun getIconForFile(file: File): Icon {
            val ext = file.extension.lowercase()
            val key = if (ext.isBlank()) "__noext__" else ext
            return fileIconCache.getOrPut(key) {
                val base: Icon = (fs.getSystemIcon(file)
                    ?: defaultFile
                    ?: UIManager.getIcon("Tree.leafIcon")
                    ?: createDefaultIcon())
                // 统一 16x16 尺寸
                IconUtil.resizeIcon(base, 16, 16)
            }
        }

        private fun createDefaultIcon(): Icon = object : Icon {
            override fun getIconWidth(): Int = 16
            override fun getIconHeight(): Int = 16
            override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics, x: Int, y: Int) {
                val g2 = g.create() as java.awt.Graphics2D
                try {
                    g2.color = java.awt.Color(120, 120, 120)
                    g2.drawRect(x + 2, y + 2, 12, 12)
                } finally { g2.dispose() }
            }
        }
    }

    // DnD: allow dropping a folder to set workspace root
    private fun installFileDropTarget() {
        try {
            val listener = object : DropTargetListener {
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
                    if (!isDropAcceptable(dtde)) { dtde.rejectDrop(); return }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    try {
                        val transferable = dtde.transferable
                        val flavor = DataFlavor.javaFileListFlavor
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(flavor) as List<File>
                        val dir = files.firstOrNull { it.isDirectory }
                        if (dir != null) {
                            mainWindow.guiControl.workspace.openWorkspace(dir)
                            refreshRoot()
                            mainWindow.statusBar.setMessage("已打开文件夹: ${dir.name}")
                            dtde.dropComplete(true)
                        } else {
                            JOptionPane.showMessageDialog(this@Explorer, "请拖拽一个文件夹到此处", "提示", JOptionPane.INFORMATION_MESSAGE)
                            dtde.dropComplete(false)
                        }
                    } catch (e: Exception) {
                        dtde.dropComplete(false)
                    }
                }
            }
            DropTarget(this, listener)
            DropTarget(tree, listener)
        } catch (_: Exception) {
        }
    }

    private fun isDragAcceptable(e: DropTargetDragEvent): Boolean {
        val flavors = e.transferable.transferDataFlavors
        val hasList = flavors.any { it.isFlavorJavaFileListType }
        if (!hasList) return false
        return true
    }

    private fun isDropAcceptable(e: DropTargetDropEvent): Boolean {
        val flavors = e.transferable.transferDataFlavors
        val hasList = flavors.any { it.isFlavorJavaFileListType }
        if (!hasList) return false
        return true
    }
}
