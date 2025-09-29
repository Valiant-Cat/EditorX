package editorx.gui.main.explorer

import editorx.filetype.FileTypeRegistry
import editorx.gui.IconRef
import editorx.gui.main.MainWindow
import editorx.util.IconLoader
import editorx.util.IconUtil
import java.awt.*
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
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class Explorer(private val mainWindow: MainWindow) : JPanel(BorderLayout()) {
    companion object {
        private const val TOP_BAR_ICON_SIZE = 16
    }

    private val searchField = JTextField()
    private val refreshBtn = JButton("刷新")
    private val showHiddenCheck = JCheckBox("显示隐藏文件")
    private val treeRoot = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(treeRoot)
    private val tree = JTree(treeModel)
    private var locateButton: JButton? = null
    private var lastKnownFile: File? = null

    // 任务取消机制
    private var currentTask: Thread? = null
    private var isTaskCancelled = false

    init {
        buildUI()
        installListeners()
        installFileDropTarget()
        showEmptyRoot()
    }

    /**
     * 取消当前的刷新任务
     */
    fun cancelRefresh() {
        currentTask?.interrupt()
        isTaskCancelled = true
        hideProgress()
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        cancelRefresh()
    }

    private fun buildUI() {
        // Compact toolbar-like header
        add(buildToolBar(), BorderLayout.NORTH)

        initFileTree()

        // 初始化定位按钮状态
        updateLocateButtonState()

        // 监听编辑器标签页变化
        setupEditorTabChangeListener()

        val scrollPane = JScrollPane(tree)
        scrollPane.border = null
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun buildToolBar(): JToolBar {
        val toolBar =
            JToolBar().apply {
                isFloatable = false
                border = EmptyBorder(0, 0, 0, 0)
                layout = BoxLayout(this, BoxLayout.X_AXIS)
            }

        /*
        Left
        */
        toolBar.add(JLabel("Explorer").apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            border = EmptyBorder(0, 8, 0, 8)
        })

        /*
        Expanded
         */
        toolBar.add(Box.createHorizontalGlue())

        /*
        Right
         */
        locateButton = JButton(IconLoader.getIcon(IconRef("icons/locate.svg"), TOP_BAR_ICON_SIZE)).apply {
            toolTipText = "定位打开的文件..."
            isFocusable = false
            margin = Insets(4, 4, 4, 4)
            addActionListener { locateCurrentFile() }
        }
        toolBar.add(locateButton!!)
        toolBar.add(JButton(IconLoader.getIcon(IconRef("icons/addFile.svg"), TOP_BAR_ICON_SIZE)).apply {
            toolTipText = "新建文件..."
            isFocusable = false
            margin = Insets(4, 4, 4, 4)
            addActionListener { createNewFile() }
        })
        toolBar.add(JButton(IconLoader.getIcon(IconRef("icons/addDirectory.svg"), TOP_BAR_ICON_SIZE)).apply {
            toolTipText = "新建文件夹..."
            isFocusable = false
            margin = Insets(4, 4, 4, 4)
            addActionListener { createNewFolder() }
        })
        toolBar.add(JButton(IconLoader.getIcon(IconRef("icons/refresh.svg"), TOP_BAR_ICON_SIZE)).apply {
            toolTipText = "刷新文件树..."
            isFocusable = false
            margin = Insets(4, 4, 4, 4)
            addActionListener { refreshRootPreserveSelection() }
        })
        toolBar.add(JButton(IconLoader.getIcon(IconRef("icons/collapseAll.svg"), TOP_BAR_ICON_SIZE)).apply {
            toolTipText = "全部收起..."
            isFocusable = false
            margin = Insets(4, 4, 4, 4)
            addActionListener { collapseAll() }
        })

        return toolBar
    }

    private fun initFileTree() {
        tree.isRootVisible = true
        tree.showsRootHandles = true
        // 单击只选中；双击才展开/收起。点击左侧的展开图标仍然是单击生效（JTree 默认行为）
        tree.toggleClickCount = 2
        // 统一展开/收缩控制的缩进，使手柄在同一列对齐
        try {
            UIManager.put("Tree.leftChildIndent", 10)
            UIManager.put("Tree.rightChildIndent", 8)
            tree.updateUI()
        } catch (_: Exception) {
        }
        tree.cellRenderer = FileTreeCellRenderer()
    }

    private fun locateCurrentFile() {
        // 获取当前编辑器中打开的文件
        val currentFile = mainWindow.editor.getCurrentFile()
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this, "当前没有打开的文件", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        // 检查文件是否在工作区根目录下
        val workspaceRoot = mainWindow.guiControl.workspace.getWorkspaceRoot()
        if (workspaceRoot == null) {
            JOptionPane.showMessageDialog(this, "请先打开工作区", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        if (!currentFile.absolutePath.startsWith(workspaceRoot.absolutePath)) {
            JOptionPane.showMessageDialog(this, "当前文件不在工作区中", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        // 在文件树中定位并选中该文件
        selectFileInTree(currentFile)
    }

    private fun updateLocateButtonState() {
        val currentFile = mainWindow.editor.getCurrentFile()
        val workspaceRoot = mainWindow.guiControl.workspace.getWorkspaceRoot()

        val isEnabled = currentFile != null &&
                workspaceRoot != null &&
                currentFile.absolutePath.startsWith(workspaceRoot.absolutePath)

        locateButton?.isEnabled = isEnabled
    }

    private fun collapseAll() {
        // 收起所有展开的节点
        val root = treeModel.root as? DefaultMutableTreeNode ?: return

        // 递归收起所有节点
        collapseAllNodes(root)

        // 刷新树显示
        treeModel.reload()
    }

    private fun collapseAllNodes(node: DefaultMutableTreeNode) {
        // 收起当前节点
        val path = TreePath(node.path)
        tree.collapsePath(path)

        // 递归处理所有子节点
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode
            if (child != null) {
                collapseAllNodes(child)
            }
        }
    }

    // 设置编辑器标签页变化监听器
    private fun setupEditorTabChangeListener() {
        // 由于无法直接访问编辑器的私有 tabbedPane，我们采用轮询方式
        // 但使用更智能的轮询策略
        val timer = Timer(500) { // 每500ms检查一次
            val currentFile = mainWindow.editor.getCurrentFile()
            // 只有当文件状态真正发生变化时才通知
            if (currentFile != lastKnownFile) {
                lastKnownFile = currentFile
                updateLocateButtonState()
            }
        }
        timer.isRepeats = true
        timer.start()
    }

    private fun createNewFile() {
        val targetDir = getCurrentSelectedDirectory()
        if (targetDir == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个目录", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val fileName = JOptionPane.showInputDialog(this, "请输入文件名:", "新建文件", JOptionPane.QUESTION_MESSAGE)
        if (fileName != null && fileName.isNotBlank()) {
            val newFile = File(targetDir, fileName.trim())
            try {
                if (newFile.createNewFile()) {
                    refreshRootPreserveSelection()
                    // 选中新创建的文件
                    selectFileInTree(newFile)
                } else {
                    JOptionPane.showMessageDialog(this, "文件已存在或创建失败", "错误", JOptionPane.ERROR_MESSAGE)
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "创建文件失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun createNewFolder() {
        val targetDir = getCurrentSelectedDirectory()
        if (targetDir == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个目录", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val folderName =
            JOptionPane.showInputDialog(this, "请输入文件夹名:", "新建文件夹", JOptionPane.QUESTION_MESSAGE)
        if (folderName != null && folderName.isNotBlank()) {
            val newFolder = File(targetDir, folderName.trim())
            try {
                if (newFolder.mkdirs()) {
                    refreshRootPreserveSelection()
                    // 选中新创建的文件夹
                    selectFileInTree(newFolder)
                } else {
                    JOptionPane.showMessageDialog(this, "文件夹已存在或创建失败", "错误", JOptionPane.ERROR_MESSAGE)
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "创建文件夹失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun getCurrentSelectedDirectory(): File? {
        val selectedPath = tree.selectionPath
        if (selectedPath != null) {
            val selectedNode = selectedPath.lastPathComponent as? DefaultMutableTreeNode
            if (selectedNode?.userObject is File) {
                val selectedFile = selectedNode.userObject as File
                return if (selectedFile.isDirectory) selectedFile else selectedFile.parentFile
            }
        }
        // 如果没有选中任何节点，使用工作区根目录
        return mainWindow.guiControl.workspace.getWorkspaceRoot()
    }

    private fun selectFileInTree(file: File) {
        // 在树中查找并选中指定的文件
        val root = treeModel.root as? DefaultMutableTreeNode ?: return
        val targetPath = findNodeForFile(root, file)
        if (targetPath != null) {
            tree.selectionPath = TreePath(targetPath)
            tree.scrollPathToVisible(TreePath(targetPath))
        }
    }

    private fun findNodeForFile(root: DefaultMutableTreeNode, targetFile: File): Array<Any>? {
        val fileNode = root as? FileNode
        if (fileNode?.represents(targetFile) == true) {
            @Suppress("UNCHECKED_CAST")
            return root.path as Array<Any>
        }

        fileNode?.loadChildrenIfNeeded(showHiddenCheck.isSelected)

        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val result = findNodeForFile(child, targetFile)
            if (result != null) return result
        }
        return null
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
        // 取消之前的任务
        currentTask?.interrupt()
        isTaskCancelled = false

        val previousState = captureTreeState()

        showProgress(message = "正在刷新文件树...", indeterminate = true)

        currentTask = Thread {
            try {
                val rootDir = mainWindow.guiControl.workspace.getWorkspaceRoot()
                if (rootDir == null || !rootDir.exists()) {
                    SwingUtilities.invokeLater { showEmptyRoot() }
                    return@Thread
                }

                val newRoot = FileNode(rootDir)
                newRoot.loadChildrenIfNeeded(showHiddenCheck.isSelected)

                // 检查是否被取消
                if (isTaskCancelled) return@Thread

                // 在EDT中更新UI
                SwingUtilities.invokeLater {
                    if (!isTaskCancelled) {
                        treeModel.setRoot(newRoot)
                        tree.isEnabled = true
                        tree.updateUI()
                        applyTreeState(previousState, newRoot)
                    }
                }
            } catch (e: InterruptedException) {
                // 任务被取消，正常退出
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "刷新文件树时出错: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } finally {
                SwingUtilities.invokeLater { hideProgress() }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun showEmptyRoot() {
        val placeholder = DefaultMutableTreeNode("未打开工作区")
        treeModel.setRoot(placeholder)
        tree.isEnabled = true
        tree.updateUI()
    }

    private fun refreshRootPreserveSelection() {
        // 取消之前的任务
        currentTask?.interrupt()
        isTaskCancelled = false

        showProgress(message = "正在刷新文件树...", indeterminate = true)

        val previousState = captureTreeState()

        currentTask = Thread {
            try {
                val rootDir = mainWindow.guiControl.workspace.getWorkspaceRoot()
                if (rootDir == null || !rootDir.exists()) {
                    SwingUtilities.invokeLater { showEmptyRoot() }
                    return@Thread
                }

                val newRoot = FileNode(rootDir)
                newRoot.loadChildrenIfNeeded(showHiddenCheck.isSelected)

                // 检查是否被取消
                if (isTaskCancelled) return@Thread

                // 在EDT中更新UI
                SwingUtilities.invokeLater {
                    if (!isTaskCancelled) {
                        treeModel.setRoot(newRoot)
                        tree.isEnabled = true
                        tree.updateUI()
                        applyTreeState(previousState, newRoot)
                    }
                }
            } catch (e: InterruptedException) {
                // 任务被取消，正常退出
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "刷新文件树时出错: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } finally {
                SwingUtilities.invokeLater { hideProgress() }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun togglePath(path: TreePath) {
        if (tree.isExpanded(path)) tree.collapsePath(path) else tree.expandPath(path)
    }

    private fun selectFirstMatch(q: String) {
        if (q.isEmpty()) return
        val root = treeModel.root as? FileNode ?: return
        val node = depthFirstSearch(root) { it.displayName.contains(q, ignoreCase = true) }
        if (node != null) {
            val tp = TreePath(node.path)
            expandTo(tp)
            tree.selectionPath = tp
            tree.scrollPathToVisible(tp)
        }
    }

    private fun selectFile(target: File) {
        val root = treeModel.root as? FileNode ?: return
        val node = depthFirstSearch(root) { it.represents(target) }
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

        fun collectExpandedPaths(node: FileNode) {
            val nodePath = node.file.absolutePath
            if (tree.isExpanded(TreePath(node.path))) {
                expandedPaths.add(nodePath)
            }
            node.loadChildrenIfNeeded(showHiddenCheck.isSelected)
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as FileNode
                collectExpandedPaths(child)
            }
        }

        collectExpandedPaths(root)
        return expandedPaths
    }

    private fun restoreExpandedPaths(expandedPaths: Set<String>) {
        val root = treeModel.root as? FileNode ?: return

        fun expandMatchingPaths(node: FileNode) {
            val nodePath = node.file.absolutePath
            if (expandedPaths.contains(nodePath)) {
                tree.expandPath(TreePath(node.path))
            }
            node.loadChildrenIfNeeded(showHiddenCheck.isSelected)
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as FileNode
                expandMatchingPaths(child)
            }
        }

        expandMatchingPaths(root)
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

    private data class TreeState(
        val expandedPaths: Set<String>,
        val selectedFilePath: String?
    )

    private fun captureTreeState(): TreeState? {
        val root = treeModel.root as? FileNode ?: return null
        val expanded = getExpandedPaths()
        val selected = (tree.lastSelectedPathComponent as? FileNode)?.file?.absolutePath
        return TreeState(expanded, selected)
    }

    private fun applyTreeState(state: TreeState?, newRoot: FileNode) {
        if (state == null) {
            SwingUtilities.invokeLater {
                val rootPath = TreePath(newRoot.path)
                tree.expandPath(rootPath)
                tree.selectionPath = rootPath
            }
            return
        }

        restoreExpandedPaths(state.expandedPaths)
        state.selectedFilePath?.let { path ->
            val target = File(path)
            SwingUtilities.invokeLater { selectFile(target) }
        }
    }

    private fun openFile(file: File) {
        try {
            mainWindow.editor.openFile(file)
            mainWindow.guiControl.workspace.addRecentFile(file)
            // 文件打开后更新定位按钮状态
            updateLocateButtonState()
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
    private class FileNode(
        val file: File,
        val displayName: String = file.name.ifEmpty { file.absolutePath },
        representedFiles: List<File> = listOf(file)
    ) : DefaultMutableTreeNode(file) {
        private var loaded = false
        private val representedPaths = representedFiles.map { it.absolutePath }.toSet()

        override fun isLeaf(): Boolean = file.isFile

        fun represents(target: File): Boolean = representedPaths.contains(target.absolutePath)

        fun loadChildrenIfNeeded(showHidden: Boolean) {
            if (loaded || file.isFile) return
            loaded = true
            removeAllChildren()
            val children = file.visibleChildren(showHidden)
            children.forEach { child ->
                if (child.isDirectory) {
                    val compressed = compressDirectoryChain(child, showHidden)
                    add(
                        FileNode(
                            compressed.finalDir,
                            compressed.displayName,
                            compressed.representedFiles
                        )
                    )
                } else {
                    add(FileNode(child))
                }
            }
        }

        fun reload(showHidden: Boolean) {
            loaded = false
            loadChildrenIfNeeded(showHidden)
        }

        override fun toString(): String = displayName

        private fun File.visibleChildren(showHidden: Boolean): List<File> =
            listFiles()
                ?.asSequence()
                ?.filter { showHidden || !it.name.startsWith(".") }
                ?.sortedWith(
                    compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                )
                ?.toList()
                ?: emptyList()

        private fun compressDirectoryChain(start: File, showHidden: Boolean): CompressedDirectory {
            val chainFiles = mutableListOf<File>()
            var current = start
            while (true) {
                chainFiles.add(current)
                val children = current.visibleChildren(showHidden)
                if (children.size != 1) break
                val onlyChild = children.first()
                if (!onlyChild.isDirectory) break
                if (chainFiles.any { it.absolutePath == onlyChild.absolutePath }) break
                current = onlyChild
            }
            val display = chainFiles.joinToString("/") { it.name.ifEmpty { it.absolutePath } }
            return CompressedDirectory(current, display, chainFiles)
        }

        private data class CompressedDirectory(
            val finalDir: File,
            val displayName: String,
            val representedFiles: List<File>
        )
    }

    /** 自定义渲染：为目录和常见后缀的文件展示系统图标（带缓存）。 */
    private class FileTreeCellRenderer : javax.swing.tree.DefaultTreeCellRenderer() {
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
            val node = value as? FileNode
            val file = node?.file
            if (file != null) {
                icon = getIconForFile(file)
                text = node.displayName
            }
            return c
        }

        private fun getIconForFile(file: File): Icon? {
            if (file.isDirectory) {
                return fileIconCache.getOrPut("folder") {
                    val base: Icon = ExplorerIcons.Folder ?: createDefaultIcon()
                    IconUtil.resizeIcon(base, 16, 16)
                }
            }

            val ext = file.extension.lowercase()
            val key = if (ext.isBlank()) "__noext__" else ext

            // Prefer plugin-provided file type icons
            val ft = FileTypeRegistry.getFileTypeByFileName(file.name)
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
