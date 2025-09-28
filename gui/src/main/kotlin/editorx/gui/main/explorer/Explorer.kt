package editorx.gui.main.explorer

import editorx.file.FileTypeRegistry
import editorx.gui.main.MainWindow
import editorx.util.IconLoader
import editorx.util.IconUtil
import editorx.vfs.LocalVirtualFile
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.filechooser.FileSystemView
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class Explorer(private val mainWindow: MainWindow) : JPanel(BorderLayout()) {

    private val searchField = JTextField()
    private val refreshBtn = JButton("刷新")
    private val showHiddenCheck = JCheckBox("显示隐藏文件")
    private val treeRoot = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(treeRoot)
    private val tree = JTree(treeModel)

    // 任务取消机制
    private var currentTask: Thread? = null
    private var isTaskCancelled = false

    init {
        buildUI()
        installListeners()
        installFileDropTarget()
        showEmptyRoot()
    }

    private fun buildUI() {
        // Compact toolbar-like header
        val toolBar =
            JToolBar().apply {
                isFloatable = false
                border = EmptyBorder(4, 4, 4, 0)
                layout = BoxLayout(this, BoxLayout.X_AXIS)
            }

        // Search field with placeholder; stretch horizontally
        searchField.columns = 16
        searchField.putClientProperty("JTextField.placeholderText", "搜索...")
        searchField.maximumSize =
            java.awt.Dimension(Int.MAX_VALUE, searchField.preferredSize.height)
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
        searchField.addKeyListener(
            object : KeyAdapter() {
                override fun keyReleased(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ESCAPE) searchField.text = ""
                }
            }
        )

        tree.addTreeWillExpandListener(
            object : TreeWillExpandListener {
                override fun treeWillExpand(event: TreeExpansionEvent) {
                    val node = event.path.lastPathComponent as? FileNode ?: return
                    node.loadChildrenIfNeeded(showHiddenCheck.isSelected)
                }

                override fun treeWillCollapse(event: TreeExpansionEvent) {}
            }
        )

        tree.addMouseListener(
            object : MouseAdapter() {
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
            }
        )

        tree.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    val node = (tree.lastSelectedPathComponent as? FileNode) ?: return
                    when (e.keyCode) {
                        KeyEvent.VK_ENTER -> if (node.file.isFile) openFile(node.file)
                        KeyEvent.VK_DELETE -> deleteNode(node)
                        KeyEvent.VK_BACK_SPACE -> reveal(node)
                    }
                }
            }
        )
    }

    fun refreshRoot() {
        showProgress(message = "正在刷新文件树...", indeterminate = true)
        try {
            val rootDir = mainWindow.guiControl.workspace.getWorkspaceRoot()
            if (rootDir == null || !rootDir.exists()) {
                showEmptyRoot()
                return
            }
            val newRoot = FileNode(rootDir)
            newRoot.loadChildrenIfNeeded(showHiddenCheck.isSelected)
            treeModel.setRoot(newRoot)
            tree.isEnabled = true
            tree.expandPath(TreePath(treeModel.root))
            tree.selectionPath = TreePath(treeModel.root)
            tree.updateUI()
        } finally {
            hideProgress()
        }
    }

    private fun showEmptyRoot() {
        val placeholder = DefaultMutableTreeNode("未打开工作区 — 将文件夹拖拽到此处")
        treeModel.setRoot(placeholder)
        tree.isEnabled = true
        tree.updateUI()
    }

    private fun refreshRootPreserveSelection() {
        val selFile = (tree.lastSelectedPathComponent as? FileNode)?.file
        val expandedPaths = getExpandedPaths()
        refreshRoot()
        restoreExpandedPaths(expandedPaths)
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
            expandTo(tp)
            tree.selectionPath = tp
            tree.scrollPathToVisible(tp)
        }
    }

    private fun selectFile(target: File) {
        val root = treeModel.root as? FileNode ?: return
        val node = depthFirstSearch(root) { it.file == target }
        if (node != null) {
            val tp = TreePath(node.path)
            expandTo(tp)
            tree.selectionPath = tp
            tree.scrollPathToVisible(tp)
        }
    }

    private fun expandTo(path: TreePath) {
        var p: TreePath? = path.parentPath
        while (p != null) {
            tree.expandPath(p)
            p = p.parentPath
        }
    }

    private fun getExpandedPaths(): Set<String> {
        val expandedPaths = mutableSetOf<String>()
        val root = treeModel.root as? FileNode ?: return expandedPaths

        fun collectExpandedPaths(node: FileNode, currentPath: String) {
            val nodePath =
                if (currentPath.isEmpty()) node.file.name else "$currentPath/${node.file.name}"
            if (tree.isExpanded(TreePath(node.path))) {
                expandedPaths.add(nodePath)
            }
            node.loadChildrenIfNeeded(showHiddenCheck.isSelected)
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as FileNode
                collectExpandedPaths(child, nodePath)
            }
        }

        collectExpandedPaths(root, "")
        return expandedPaths
    }

    private fun restoreExpandedPaths(expandedPaths: Set<String>) {
        val root = treeModel.root as? FileNode ?: return

        fun expandMatchingPaths(node: FileNode, currentPath: String) {
            val nodePath =
                if (currentPath.isEmpty()) node.file.name else "$currentPath/${node.file.name}"
            if (expandedPaths.contains(nodePath)) {
                tree.expandPath(TreePath(node.path))
            }
            node.loadChildrenIfNeeded(showHiddenCheck.isSelected)
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as FileNode
                expandMatchingPaths(child, nodePath)
            }
        }

        expandMatchingPaths(root, "")
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
            JOptionPane.showMessageDialog(
                this,
                "打开文件失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
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
        } finally {
            setWait(false)
        }
    }

    private fun deleteNode(node: FileNode) {
        val f = node.file
        val confirm =
            JOptionPane.showConfirmDialog(
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
            JOptionPane.showMessageDialog(
                this,
                "删除失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        } finally {
            setWait(false)
        }
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
        cursor =
            if (wait) Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
            else Cursor.getDefaultCursor()
    }

    private fun showProgress(
        message: String,
        indeterminate: Boolean = true,
        cancellable: Boolean = false,
        maximum: Int = 100
    ) {
        SwingUtilities.invokeLater {
            mainWindow.statusBar.showProgress(
                message,
                indeterminate,
                cancellable,
                { cancelCurrentTask() },
                maximum
            )
        }
    }

    private fun updateProgress(value: Int, message: String? = null) {
        SwingUtilities.invokeLater {
            if (message != null) {
                mainWindow.statusBar.updateProgress(value, message)
            } else {
                mainWindow.statusBar.updateProgress(value, "处理中...")
            }
        }
    }

    private fun hideProgress() {
        SwingUtilities.invokeLater { mainWindow.statusBar.hideProgress() }
    }

    private fun cancelCurrentTask() {
        isTaskCancelled = true
        currentTask?.interrupt()
        hideProgress()
        setWait(false)
        mainWindow.statusBar.setMessage("任务已取消")
    }

    private fun handleApkFile(apkFile: File) {

        // 重置取消状态
        isTaskCancelled = false

        // 在后台线程中处理APK反编译
        currentTask = Thread {
            try {
                showProgress(message = "正在反编译APK...", indeterminate = false, cancellable = true, maximum = 1)

                // 检查是否被取消
                if (!isTaskCancelled && !Thread.currentThread().isInterrupted) {
                    updateProgress(1, "正在反编译: ${apkFile.name}")
                    decompileApk(apkFile)
                }

                // 检查是否被取消
                if (!isTaskCancelled && !Thread.currentThread().isInterrupted) {
                    SwingUtilities.invokeLater {
                        hideProgress()
                        mainWindow.statusBar.setMessage("APK反编译完成")
                        refreshRoot()
                    }
                }
            } catch (e: Exception) {
                if (!isTaskCancelled) {
                    SwingUtilities.invokeLater {
                        hideProgress()
                        JOptionPane.showMessageDialog(
                            this,
                            "APK反编译失败: ${e.message}",
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }
        currentTask?.start()

    }

    private fun decompileApk(apkFile: File) {
        try {
            // 检查是否被取消
            if (isTaskCancelled || Thread.currentThread().isInterrupted) {
                return
            }

            // 检查apktool是否可用
            val apktoolPath = findApktool()
            if (apktoolPath == null) {
                throw Exception("未找到apktool，请确保apktool已安装并在PATH中")
            }

            // 创建输出目录
            val outputDir = File(apkFile.parentFile, apkFile.nameWithoutExtension + "_decompiled")
            if (outputDir.exists()) {
                // 如果目录已存在，询问是否覆盖
                val overwrite =
                    JOptionPane.showConfirmDialog(
                        this,
                        "目录 ${outputDir.name} 已存在，是否覆盖？",
                        "确认覆盖",
                        JOptionPane.YES_NO_OPTION
                    ) == JOptionPane.YES_OPTION

                if (!overwrite) return
            }

            // 再次检查是否被取消
            if (isTaskCancelled || Thread.currentThread().isInterrupted) {
                return
            }

            showProgress(message = "正在反编译APK...", indeterminate = true, cancellable = true)

            // 删除现有目录
            if (outputDir.exists()) deleteRecursively(outputDir)

            // 执行apktool反编译
            val processBuilder =
                ProcessBuilder(
                    apktoolPath,
                    "d",
                    apkFile.absolutePath,
                    "-o",
                    outputDir.absolutePath,
                    "-f" // 强制覆盖
                )

            val process = processBuilder.start()

            // 在等待过程中检查取消状态
            while (process.isAlive) {
                if (isTaskCancelled || Thread.currentThread().isInterrupted) {
                    process.destroy()
                    return
                }
                Thread.sleep(100) // 短暂等待
            }

            val exitCode = process.exitValue()

            if (exitCode != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText()
                throw Exception("apktool执行失败: $errorOutput")
            }

            // 反编译成功后，打开输出目录
            if (!isTaskCancelled && !Thread.currentThread().isInterrupted) {
                SwingUtilities.invokeLater {
                    mainWindow.guiControl.workspace.openWorkspace(outputDir)
                    refreshRoot()
                    mainWindow.statusBar.setMessage("APK反编译完成: ${outputDir.name}")
                }
            }
        } catch (e: Exception) {
            if (!isTaskCancelled) {
                throw Exception("反编译APK失败: ${e.message}")
            }
        }
    }

    private fun findApktool(): String? {
        // 首先检查项目tools目录下的apktool
        val projectRoot = File(System.getProperty("user.dir"))
        val localApktool = File(projectRoot, "tools/apktool")
        if (localApktool.exists() && localApktool.canExecute()) {
            return localApktool.absolutePath
        }

        // 然后检查PATH中的apktool
        try {
            val process = ProcessBuilder("apktool", "--version").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                return "apktool"
            }
        } catch (e: Exception) {
            // apktool不在PATH中，继续查找
        }

        // 检查常见安装位置
        val commonPaths =
            listOf(
                "/usr/local/bin/apktool",
                "/opt/homebrew/bin/apktool",
                "/usr/bin/apktool",
                System.getProperty("user.home") + "/.local/bin/apktool"
            )

        for (path in commonPaths) {
            if (File(path).exists()) {
                return path
            }
        }

        return null
    }

    // Node representing a File with lazy children loading
    private class FileNode(val file: File) : DefaultMutableTreeNode(file) {
        private var loaded = false

        override fun isLeaf(): Boolean = file.isFile

        fun loadChildrenIfNeeded(showHidden: Boolean) {
            if (loaded || file.isFile) return
            loaded = true
            removeAllChildren()
            val children =
                file.listFiles()
                    ?.asSequence()
                    ?.filter { showHidden || !it.name.startsWith(".") }
                    ?.sortedWith(
                        compareBy<File> { !it.isDirectory }.thenBy {
                            it.name.lowercase()
                        }
                    )
                    ?.toList()
                    ?: emptyList()
            children.forEach { add(FileNode(it)) }
        }

        fun reload(showHidden: Boolean) {
            loaded = false
            loadChildrenIfNeeded(showHidden)
        }

        override fun toString(): String = file.name.ifEmpty { file.absolutePath }
    }

    /** 自定义渲染：为目录和常见后缀的文件展示系统图标（带缓存）。 */
    private class FileTreeCellRenderer : javax.swing.tree.DefaultTreeCellRenderer() {
        private val fs = FileSystemView.getFileSystemView()
        private val fileIconCache = mutableMapOf<String, Icon>()

        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): java.awt.Component {
            val c =
                super.getTreeCellRendererComponent(
                    tree,
                    value,
                    sel,
                    expanded,
                    leaf,
                    row,
                    hasFocus
                )
            val node = value as? DefaultMutableTreeNode
            val file = (node as? FileNode)?.file
            if (file != null) {
                icon = getIconForFile(file)
                text = file.name.ifEmpty { file.absolutePath }
            }
            return c
        }

        private fun getIconForFile(file: File): Icon {
            if (file.isDirectory) {
                return fileIconCache.getOrPut("folder") {
                    val base: Icon = ExplorerIcons.Folder ?: createDefaultIcon()
                    IconUtil.resizeIcon(base, 16, 16)
                }
            }

            val ext = file.extension.lowercase()
            val key = if (ext.isBlank()) "__noext__" else ext

            // Prefer plugin-provided file type icons
            val vt = LocalVirtualFile.of(file)
            val ft = FileTypeRegistry.getByFile(vt)
            if (ft != null) {
                val ref = ft.getIcon()
                val ico = IconLoader.getIcon(ref, 16)
                if (ico != null) return ico
            }

            return fileIconCache.getOrPut(key) {
                val base: Icon = ExplorerIcons.AnyType ?: createDefaultIcon()
                IconUtil.resizeIcon(base, 16, 16)
            }
        }

        private fun createDefaultIcon(): Icon =
            object : Icon {
                override fun getIconWidth(): Int = 16
                override fun getIconHeight(): Int = 16
                override fun paintIcon(
                    c: java.awt.Component?,
                    g: java.awt.Graphics,
                    x: Int,
                    y: Int
                ) {
                    val g2 = g.create() as java.awt.Graphics2D
                    try {
                        g2.color = java.awt.Color(120, 120, 120)
                        g2.drawRect(x + 2, y + 2, 12, 12)
                    } finally {
                        g2.dispose()
                    }
                }
            }
    }

    // DnD: allow dropping a folder to set workspace root
    private fun installFileDropTarget() {
        try {
            val listener =
                object : DropTargetListener {
                    override fun dragEnter(dtde: DropTargetDragEvent) {
                        if (isDragAcceptable(dtde)) dtde.acceptDrag(DnDConstants.ACTION_COPY)
                        else dtde.rejectDrag()
                    }

                    override fun dragOver(dtde: DropTargetDragEvent) {
                        if (isDragAcceptable(dtde)) dtde.acceptDrag(DnDConstants.ACTION_COPY)
                        else dtde.rejectDrag()
                    }

                    override fun dropActionChanged(dtde: DropTargetDragEvent) {
                        if (isDragAcceptable(dtde)) dtde.acceptDrag(DnDConstants.ACTION_COPY)
                        else dtde.rejectDrag()
                    }

                    override fun dragExit(dtde: DropTargetEvent) {}
                    override fun drop(dtde: DropTargetDropEvent) {
                        if (!isDropAcceptable(dtde)) {
                            dtde.rejectDrop()
                            return
                        }
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        try {
                            val transferable = dtde.transferable
                            val flavor = DataFlavor.javaFileListFlavor

                            @Suppress("UNCHECKED_CAST")
                            val files = transferable.getTransferData(flavor) as List<File>

                            // 检查是否拖拽的是单个APK文件（不支持目录）
                            if (files.size == 1) {
                                val file = files[0]
                                if (file.isFile && file.extension.lowercase() == "apk") {
                                    handleApkFile(file)
                                    dtde.dropComplete(true)
                                    return
                                }
                            }

                            // 如果没有找到APK文件，检查是否有文件夹可以打开为工作区
                            val dir = files.firstOrNull { it.isDirectory }
                            if (dir != null) {
                                mainWindow.guiControl.workspace.openWorkspace(dir)
                                refreshRoot()
                                mainWindow.statusBar.setMessage("已打开文件夹: ${dir.name}")
                                dtde.dropComplete(true)
                            } else {
                                JOptionPane.showMessageDialog(
                                    this@Explorer,
                                    "请拖拽一个文件夹或APK文件到此处",
                                    "提示",
                                    JOptionPane.INFORMATION_MESSAGE
                                )
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

        // 检查是否包含APK文件或文件夹
        try {
            val files = e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
            if (files != null) {
                val hasApk = files.any { it.isFile && it.extension.lowercase() == "apk" }
                val hasDir = files.any { it.isDirectory }
                return hasApk || hasDir
            }
        } catch (e: Exception) {
            // 忽略异常，继续检查
        }

        return true
    }

    private fun isDropAcceptable(e: DropTargetDropEvent): Boolean {
        val flavors = e.transferable.transferDataFlavors
        val hasList = flavors.any { it.isFlavorJavaFileListType }
        if (!hasList) return false

        // 检查是否包含APK文件或文件夹
        try {
            val files = e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
            if (files != null) {
                val hasApk = files.any { it.isFile && it.extension.lowercase() == "apk" }
                val hasDir = files.any { it.isDirectory }
                return hasApk || hasDir
            }
        } catch (e: Exception) {
            // 忽略异常，继续检查
        }

        return true
    }

}
