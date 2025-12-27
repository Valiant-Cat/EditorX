package editorx.gui.workbench.explorer

import editorx.core.external.ApkTool
import editorx.gui.core.FileTypeManager
import editorx.gui.core.FileHandlerManager
import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import editorx.core.util.IconUtils
import editorx.gui.theme.ThemeManager
import editorx.gui.MainWindow
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
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
    private val treeRoot = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(treeRoot)
    private val tree = JTree(treeModel)
    private var locateButton: JButton? = null
    private var refreshButton: JButton? = null
    private var collapseButton: JButton? = null
    private var lastKnownFile: File? = null

    // 任务取消机制
    private var currentTask: Thread? = null
    private var isTaskCancelled = false

    private var titleLabel: JLabel? = null

    init {
        isOpaque = false  // 使 Explorer 透明，显示 SideBar 的背景色
        buildUI()
        installListeners()
        installFileDropTarget()
        showEmptyRoot()
        
        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }
    }
    
    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        // 更新标题标签颜色
        titleLabel?.apply {
            foreground = theme.onSurface
            repaint()
        }
        // 更新按钮图标以适配主题
        refreshButton?.icon = IconLoader.getIcon(
            IconRef("icons/common/refresh.svg"),
            TOP_BAR_ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { theme.onSurface }
        )
        collapseButton?.icon = IconLoader.getIcon(
            IconRef("icons/common/collapseAll.svg"),
            TOP_BAR_ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { theme.onSurface }
        )
        // 刷新文件树以更新文本颜色
        tree.updateUI()
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
        scrollPane.isOpaque = false  // 使滚动面板透明，显示 SideBar 背景色
        scrollPane.viewport.isOpaque = false  // 使视口透明
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun buildToolBar(): JToolBar {
        val toolBar =
            JToolBar().apply {
                isFloatable = false
                isOpaque = false  // 使工具栏透明，显示 SideBar 的背景色
                border = EmptyBorder(0, 0, 0, 0)
                layout = BoxLayout(this, BoxLayout.X_AXIS)
            }

        /*
        Left - 可收缩部分
        */
        titleLabel = JLabel("资源管理器").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = ThemeManager.currentTheme.onSurface
            border = EmptyBorder(0, 8, 0, 8)
            // 允许标签收缩，但设置最小宽度为0
            minimumSize = Dimension(0, preferredSize.height)
        }
        toolBar.add(titleLabel!!)

        /*
        Expanded - 可伸缩空间
         */
        toolBar.add(Box.createHorizontalGlue())

        /*
        Right - 固定按钮组
         */
        locateButton = JButton(IconLoader.getIcon(IconRef("icons/common/locate.svg"), TOP_BAR_ICON_SIZE)).apply {
            toolTipText = "定位打开的文件..."
            isFocusable = false
            margin = Insets(4, 4, 4, 4)
            addActionListener { locateCurrentFile() }
        }
        toolBar.add(locateButton!!)
        refreshButton = JButton(IconLoader.getIcon(
            IconRef("icons/common/refresh.svg"),
            TOP_BAR_ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { ThemeManager.currentTheme.onSurface }
        )).apply {
            toolTipText = "刷新文件树..."
            isFocusable = false
            margin = Insets(4, 4, 4, 4)
            addActionListener { refreshRootPreserveSelection() }
        }
        toolBar.add(refreshButton!!)
        collapseButton = JButton(IconLoader.getIcon(
            IconRef("icons/common/collapseAll.svg"),
            TOP_BAR_ICON_SIZE,
            adaptToTheme = true,
            getThemeColor = { ThemeManager.currentTheme.onSurface }
        )).apply {
            toolTipText = "全部收起..."
            isFocusable = false
            margin = Insets(4, 4, 4, 4)
            addActionListener { collapseAll() }
        }
        toolBar.add(collapseButton!!)

        return toolBar
    }

    private fun initFileTree() {
        tree.isRootVisible = true
        tree.showsRootHandles = true
        // 单击只选中；双击才展开/收起。点击左侧的展开图标仍然是单击生效（JTree 默认行为）
        tree.toggleClickCount = 2
        tree.isOpaque = false  // 使 JTree 透明，显示 SideBar 的背景色
        // 统一展开/收缩控制的缩进，使手柄在同一列对齐
        try {
            UIManager.put("Tree.leftChildIndent", 10)
            UIManager.put("Tree.rightChildIndent", 8)
            tree.updateUI()
        } catch (_: Exception) {
        }
        tree.cellRenderer = FileTreeCellRenderer(mainWindow)
    }

    private fun locateCurrentFile() {
        // 获取当前编辑器中打开的文件
        val currentFile = mainWindow.editor.getCurrentFile()
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this, "当前没有打开的文件", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        // 检查文件是否在工作区根目录下
        val workspaceRoot = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()
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
        val workspaceRoot = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()

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
        createNewFileInDirectory(targetDir)
    }

    private fun createNewFileInDirectory(targetDir: File) {
        if (!targetDir.exists() || !targetDir.isDirectory) {
            JOptionPane.showMessageDialog(this, "目标目录不存在或不是目录", "错误", JOptionPane.ERROR_MESSAGE)
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
        createNewFolderInDirectory(targetDir)
    }

    private fun createNewFolderInDirectory(targetDir: File) {
        if (!targetDir.exists() || !targetDir.isDirectory) {
            JOptionPane.showMessageDialog(this, "目标目录不存在或不是目录", "错误", JOptionPane.ERROR_MESSAGE)
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
        return mainWindow.guiContext.getWorkspace().getWorkspaceRoot()
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

        fileNode?.loadChildrenIfNeeded(true)

        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val result = findNodeForFile(child, targetFile)
            if (result != null) return result
        }
        return null
    }

    private fun installListeners() {
        refreshBtn.addActionListener { refreshRoot() }
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
                    node.loadChildrenIfNeeded(true)
                }

                override fun treeWillCollapse(event: TreeExpansionEvent) {}
            }
        )

        tree.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    // 处理右键按下事件，先选中节点再显示菜单
                    if (SwingUtilities.isRightMouseButton(e)) {
                        val row = tree.getClosestRowForLocation(e.x, e.y)
                        if (row == -1) {
                            // 点击在空白区域
                            showContextMenuForEmptyArea(e.x, e.y)
                            return
                        }
                        val bounds = tree.getRowBounds(row) ?: return
                        // 仅当点击发生在该行的垂直范围内才认为命中
                        if (e.y < bounds.y || e.y >= bounds.y + bounds.height) {
                            // 点击在空白区域
                            showContextMenuForEmptyArea(e.x, e.y)
                            return
                        }
                        val path = tree.getPathForRow(row) ?: return
                        val node = path.lastPathComponent as? FileNode ?: return

                        // 先选中该节点
                        tree.selectionPath = path
                        // 然后显示菜单
                        showContextMenu(node, e.x, e.y)
                    }
                }

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
                val rootDir = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()
                if (rootDir == null || !rootDir.exists()) {
                    SwingUtilities.invokeLater { showEmptyRoot() }
                    return@Thread
                }

                val newRoot = FileNode(rootDir)
                newRoot.loadChildrenIfNeeded(true)

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
                val rootDir = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()
                if (rootDir == null || !rootDir.exists()) {
                    SwingUtilities.invokeLater { showEmptyRoot() }
                    return@Thread
                }

                val newRoot = FileNode(rootDir)
                newRoot.loadChildrenIfNeeded(true)

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

    fun focusFileInTree(target: File) {
        SwingUtilities.invokeLater { selectFile(target) }
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
            node.loadChildrenIfNeeded(true)
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
            node.loadChildrenIfNeeded(true)
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as FileNode
                expandMatchingPaths(child)
            }
        }

        expandMatchingPaths(root)
    }

    private fun depthFirstSearch(n: FileNode, pred: (FileNode) -> Boolean): FileNode? {
        if (pred(n)) return n
        n.loadChildrenIfNeeded(true)
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
            mainWindow.guiContext.getWorkspace().addRecentFile(file)
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
        if (node.file.isDirectory) {
            menu.add(JMenuItem("新建文件").apply {
                addActionListener {
                    // 确定目标目录：如果是文件，使用其父目录；如果是目录，使用该目录
                    val targetDir = if (node.file.isFile) node.file.parentFile else node.file
                    createNewFileInDirectory(targetDir)
                }
            })
            menu.add(JMenuItem("新建文件夹").apply {
                addActionListener {
                    // 确定目标目录：如果是文件，使用其父目录；如果是目录，使用该目录
                    val targetDir = if (node.file.isFile) node.file.parentFile else node.file
                    createNewFolderInDirectory(targetDir)
                }
            })
        }
        menu.add(JMenuItem("在系统中显示").apply { addActionListener { reveal(node) } })
        menu.addSeparator()
        menu.add(JMenuItem("删除").apply { addActionListener { deleteNode(node) } })
        menu.show(tree, x, y)
    }

    /**
     * 在空白区域显示右键菜单
     */
    private fun showContextMenuForEmptyArea(x: Int, y: Int) {
        val menu = JPopupMenu()
        // 获取工作区根目录作为目标目录
        val workspaceRoot = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()
        if (workspaceRoot != null) {
            menu.add(JMenuItem("新建文件").apply {
                addActionListener { createNewFileInDirectory(workspaceRoot) }
            })
            menu.add(JMenuItem("新建文件夹").apply {
                addActionListener { createNewFolderInDirectory(workspaceRoot) }
            })
            menu.addSeparator()
        }
        menu.add(JMenuItem("刷新").apply {
            addActionListener { refreshRootPreserveSelection() }
        })
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
            node.reload(true)
            treeModel.nodeStructureChanged(node)
        } finally {
            setWait(false)
        }
    }

    private fun deleteNode(node: FileNode) {
        val f = node.file
        val confirm =
            JOptionPane.showConfirmDialog(
                mainWindow,
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

    /**
     * 提示用户是否创建 Git 仓库
     */
    private fun promptCreateGitRepository(outputDir: File) {
        val response = JOptionPane.showConfirmDialog(
            mainWindow,
            "是否要为当前项目创建 Git 仓库？\n这将帮助您跟踪代码修改历史。",
            "创建 Git 仓库",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )

        if (response == JOptionPane.YES_OPTION) {
            createGitRepository(outputDir)
        }
    }

    /**
     * 创建 Git 仓库
     */
    private fun createGitRepository(outputDir: File) {
        try {
            // 检查是否已经存在 Git 仓库
            val gitDir = File(outputDir, ".git")
            if (gitDir.exists()) {
                JOptionPane.showMessageDialog(
                    this,
                    "该目录已经存在 Git 仓库",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE
                )
                return
            }

            // 在后台线程中创建 Git 仓库
            currentTask = Thread {
                try {
                    if (isTaskCancelled || Thread.currentThread().isInterrupted) return@Thread

                    setWait(true)
                    showProgress("正在创建 Git 仓库...", indeterminate = true)

                    // 检查 git 是否可用
                    if (!isGitAvailable()) {
                        SwingUtilities.invokeLater {
                            hideProgress()
                            setWait(false)
                            JOptionPane.showMessageDialog(
                                this,
                                "未找到 git 命令，请确保 git 已安装并在 PATH 中",
                                "错误",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                        return@Thread
                    }

                    if (isTaskCancelled || Thread.currentThread().isInterrupted) return@Thread

                    // 执行 git init（带超时）
                    val initResult = runGitCommand(listOf("git", "init"), outputDir, 10000)

                    if (isTaskCancelled || Thread.currentThread().isInterrupted) return@Thread

                    if (initResult.exitCode == 0) {
                        // 创建 .gitignore 文件
                        createGitIgnoreFile(outputDir)

                        if (isTaskCancelled || Thread.currentThread().isInterrupted) return@Thread

                        // 执行初始提交
                        performInitialCommit(outputDir)

                        SwingUtilities.invokeLater {
                            if (!isTaskCancelled) {
                                hideProgress()
                                setWait(false)
                                JOptionPane.showMessageDialog(
                                    this,
                                    "Git 仓库创建成功！\n\n已自动添加 .gitignore 文件并执行初始提交。",
                                    "成功",
                                    JOptionPane.INFORMATION_MESSAGE
                                )
                                mainWindow.statusBar.setMessage("Git 仓库创建完成")
                            }
                        }
                    } else {
                        SwingUtilities.invokeLater {
                            hideProgress()
                            setWait(false)
                            JOptionPane.showMessageDialog(
                                this,
                                "Git 仓库创建失败：\n${initResult.output}",
                                "错误",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                } catch (e: InterruptedException) {
                    // 任务被取消，正常退出
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        hideProgress()
                        setWait(false)
                        JOptionPane.showMessageDialog(
                            this,
                            "创建 Git 仓库时出错：${e.message}",
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
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "无法创建 Git 仓库：${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * 创建 .gitignore 文件
     */
    private fun createGitIgnoreFile(outputDir: File) {
        val gitIgnoreContent = """
            # APK 反编译相关文件
            *.apk
            *.dex
            *.odex
            *.vdex
            *.art
            
            # 构建输出
            build/
            dist/
            out/
            bin/
            gen/
            
            # IDE 文件
            .idea/
            .vscode/
            *.iml
            *.ipr
            *.iws
            
            # 系统文件
            .DS_Store
            Thumbs.db
            *.tmp
            *.temp
            
            # 日志文件
            *.log
            logs/
            
            # 临时文件
            temp/
            tmp/
            cache/
        """.trimIndent()

        val gitIgnoreFile = File(outputDir, ".gitignore")
        gitIgnoreFile.writeText(gitIgnoreContent)
    }

    /**
     * 执行初始提交
     */
    private fun performInitialCommit(outputDir: File) {
        try {
            if (isTaskCancelled || Thread.currentThread().isInterrupted) return

            // 添加所有文件
            val addResult = runGitCommand(listOf("git", "add", "."), outputDir, 30000)
            if (addResult.exitCode != 0) {
                return // 添加失败，跳过提交
            }

            if (isTaskCancelled || Thread.currentThread().isInterrupted) return

            // 检查是否有文件需要提交
            val statusResult = runGitCommand(listOf("git", "status", "--porcelain"), outputDir, 5000)

            if (statusResult.output.isNotBlank()) {
                if (isTaskCancelled || Thread.currentThread().isInterrupted) return

                // 执行初始提交
                runGitCommand(
                    listOf("git", "commit", "-m", "Initial commit: APK decompiled"),
                    outputDir,
                    30000
                )
            }
        } catch (e: Exception) {
            // 忽略提交错误，Git 仓库已经创建成功
        }
    }

    /**
     * 检查 git 是否可用
     */
    private fun isGitAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("git", "--version").start()
            val exitCode = process.waitFor(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
            exitCode && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 运行 git 命令（带超时和取消支持）
     */
    private data class GitCommandResult(val exitCode: Int, val output: String)

    private fun runGitCommand(command: List<String>, workingDir: File, timeoutMs: Long): GitCommandResult {
        val pb = ProcessBuilder(command)
        pb.directory(workingDir)
        pb.redirectErrorStream(true)

        val process = try {
            pb.start()
        } catch (e: Exception) {
            return GitCommandResult(-1, "无法启动进程: ${e.message}")
        }

        // 在后台线程读取输出，避免阻塞
        val outputBuffer = StringBuilder()
        val outputReader = Thread {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (!isTaskCancelled && !Thread.currentThread().isInterrupted) {
                            outputBuffer.append(line).append("\n")
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略读取错误
            }
        }.apply {
            isDaemon = true
            start()
        }

        // 等待进程完成（带超时）
        val startTime = System.currentTimeMillis()
        while (true) {
            if (isTaskCancelled || Thread.currentThread().isInterrupted) {
                process.destroy()
                if (process.isAlive) process.destroyForcibly()
                return GitCommandResult(-1, "操作已取消")
            }

            try {
                val exitCode = process.exitValue()
                outputReader.join(1000) // 等待输出读取完成
                return GitCommandResult(exitCode, outputBuffer.toString().trim())
            } catch (_: IllegalThreadStateException) {
                // 进程仍在运行
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    process.destroy()
                    if (process.isAlive) process.destroyForcibly()
                    return GitCommandResult(-1, "命令执行超时（超过 ${timeoutMs}ms）")
                }
                Thread.sleep(100)
            }
        }
    }

    /**
     * 处理 APK 文件转换为项目
     * 这是一个公共方法，可以从其他组件（如 WelcomeView）调用
     */
    fun handleApkFileConversion(apkFile: File) {
        // 重置取消状态
        isTaskCancelled = false

        // 在后台线程中处理APK反编译
        currentTask = Thread {
            try {
                if (!Thread.currentThread().isInterrupted) {
                    setWait(true)
                    decompileApk(apkFile)
                    setWait(false)
                    SwingUtilities.invokeLater {
                        hideProgress()
                        refreshRoot()
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    hideProgress()
                    JOptionPane.showMessageDialog(
                        this,
                        "反编译失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
        currentTask?.start()
    }

    private fun decompileApk(apkFile: File) {
        try {
            if (isTaskCancelled || Thread.currentThread().isInterrupted) return

            val outputDir = File(apkFile.parentFile, apkFile.nameWithoutExtension + "_decompiled")
            if (outputDir.exists()) {
                val options = arrayOf("打开已存在的项目", "重新反编译")
                val choice = JOptionPane.showOptionDialog(
                    mainWindow,
                    "此Apk对应的反编译目录 \"${outputDir.name}\" 已存在。\n\n请选择操作：",
                    "目录已存在",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0] // 默认选择"打开已存在的项目"
                )

                when (choice) {
                    JOptionPane.YES_OPTION -> {
                        // 直接打开已存在的项目
                        SwingUtilities.invokeLater {
                            mainWindow.guiContext.getWorkspace().openWorkspace(outputDir)
                            mainWindow.editor.updateNavigation(null)
                            refreshRoot()
                            mainWindow.titleBar.updateVcsDisplay()
                            mainWindow.statusBar.setMessage("已打开项目: ${outputDir.name}")
                        }
                        return
                    }

                    JOptionPane.NO_OPTION -> {
                        // 重新反编译，继续执行后续逻辑
                    }

                    else -> {
                        // 用户取消或关闭对话框
                        return
                    }
                }
            }

            if (isTaskCancelled || Thread.currentThread().isInterrupted) return

            showProgress(message = "正在反编译APK...", indeterminate = true, cancellable = false)
            if (outputDir.exists()) deleteRecursively(outputDir)

            val result =
                ApkTool.decompile(apkFile, outputDir, force = true) {
                    isTaskCancelled || Thread.currentThread().isInterrupted
                }

            when (result.status) {
                ApkTool.Status.SUCCESS -> {
                    // 从 APK 中提取 DEX 文件到输出目录
                    val originalDir = File(outputDir, "original")
                    extractDexFilesFromApk(apkFile, originalDir)

                    if (!isTaskCancelled && !Thread.currentThread().isInterrupted) {
                        SwingUtilities.invokeLater {
                            mainWindow.guiContext.getWorkspace().openWorkspace(outputDir)
                            mainWindow.editor.updateNavigation(null)
                            refreshRoot()
                            mainWindow.titleBar.updateVcsDisplay()
                            mainWindow.statusBar.setMessage("APK反编译完成: ${outputDir.name}")

                            // 提示是否创建 Git 仓库
                            promptCreateGitRepository(outputDir)
                        }
                    }
                }

                ApkTool.Status.CANCELLED -> return
                ApkTool.Status.NOT_FOUND -> throw Exception("未找到apktool，请确保apktool已安装并在PATH中")
                ApkTool.Status.FAILED -> throw Exception("apktool执行失败: ${result.output}")
            }
        } catch (e: Exception) {
            if (!isTaskCancelled) {
                throw Exception("反编译APK失败: ${e.message}")
            }
        }
    }

    // 从 APK 文件中提取 DEX 文件到输出目录（用于实时 smali to java 反编译）
    private fun extractDexFilesFromApk(apkFile: File, outputDir: File) {
        try {
            val logger = org.slf4j.LoggerFactory.getLogger(Explorer::class.java)
            logger.debug("开始从 APK 提取 DEX 文件: ${apkFile.absolutePath} -> ${outputDir.absolutePath}")

            val zipFile = ZipFile(apkFile)
            val dexPattern = """^classes\d*\.dex$""".toRegex()
            var extractedCount = 0
            val extractedFiles = mutableListOf<String>()

            zipFile.entries().asSequence().forEach { entry ->
                if (dexPattern.matches(entry.name)) {
                    val outputDexFile = File(outputDir, entry.name)
                    try {
                        zipFile.getInputStream(entry).use { input ->
                            FileOutputStream(outputDexFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        extractedCount++
                        extractedFiles.add(entry.name)
                        logger.debug("提取 DEX 文件: ${entry.name} -> ${outputDexFile.absolutePath}")
                    } catch (e: Exception) {
                        logger.warn("提取 DEX 文件失败: ${entry.name}", e)
                    }
                }
            }

            zipFile.close()

            if (extractedCount > 0) {
                logger.info("从 APK 成功提取了 $extractedCount 个 DEX 文件到: ${outputDir.absolutePath}")
                logger.debug("提取的文件: ${extractedFiles.joinToString(", ")}")
            } else {
                logger.warn("未找到任何 DEX 文件在 APK 中: ${apkFile.absolutePath}")
            }
        } catch (e: Exception) {
            val logger = org.slf4j.LoggerFactory.getLogger(Explorer::class.java)
            logger.error("提取 DEX 文件失败: ${e.message}", e)
            // 不抛出异常，因为反编译本身已经成功
        }
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
                ?.filter { file ->
                    if (showHidden) {
                        true // 显示所有文件
                    } else {
                        // 隐藏文件：以 . 开头（Unix/Linux/macOS）或 isHidden 为 true（Windows）
                        !file.name.startsWith(".") && !file.isHidden
                    }
                }
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
    private class FileTreeCellRenderer(private val mainWindow: MainWindow) :
        javax.swing.tree.DefaultTreeCellRenderer() {
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
            // 设置文本颜色为主题颜色（如果未选中）
            if (!sel) {
                foreground = ThemeManager.currentTheme.onSurface
            }
            return c
        }

        private fun getIconForFile(file: File): Icon? {
            if (file.isDirectory) {
                // 检查是否是根目录（assets、res、smali）
                val workspaceRoot = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()
                if (workspaceRoot != null && file.parentFile?.absolutePath == workspaceRoot.absolutePath) {
                    val dirName = file.name.lowercase()
                    when {
                        dirName == "assets" || dirName == "res" -> {
                            // 使用 resourcesRoot 图标
                            return fileIconCache.getOrPut("resourcesRoot") {
                                val base: Icon =
                                    ExplorerIcons.ResourcesRoot ?: ExplorerIcons.Folder ?: createDefaultIcon()
                                IconUtils.resizeIcon(base, 16, 16)
                            }
                        }

                        dirName == "smali" || dirName.startsWith("smali_") -> {
                            // 使用 sourceRoot 图标（包括 smali、smali_classes2 等）
                            return fileIconCache.getOrPut("sourceRoot") {
                                val base: Icon = ExplorerIcons.SourceRoot ?: ExplorerIcons.Folder ?: createDefaultIcon()
                                IconUtils.resizeIcon(base, 16, 16)
                            }
                        }
                    }
                }
                // 普通目录
                return fileIconCache.getOrPut("folder") {
                    val base: Icon = ExplorerIcons.Folder ?: createDefaultIcon()
                    IconUtils.resizeIcon(base, 16, 16)
                }
            }

            val ext = file.extension.lowercase()
            val key = if (ext.isBlank()) "__noext__" else ext

            // Prefer plugin-provided file type icons
            val ft = FileTypeManager.getFileTypeByFileName(file.name)
            if (ft != null) {
                val icon = ft.getIcon()
                if (icon != null) return icon
            }

            return fileIconCache.getOrPut(key) {
                val base: Icon = ExplorerIcons.AnyType ?: createDefaultIcon()
                IconUtils.resizeIcon(base, 16, 16)
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

                            // 检查是否拖拽的是单个文件，先询问文件处理器
                            if (files.size == 1) {
                                val file = files[0]
                                if (file.isFile && file.canRead()) {
                                    if (FileHandlerManager.handleOpenFile(file)) {
                                        dtde.dropComplete(true)
                                        return
                                    }
                                }
                            }

                            // 如果没有找到APK文件，检查是否有文件夹可以打开为工作区
                            val dir = files.firstOrNull { it.isDirectory }
                            if (dir != null) {
                                mainWindow.guiContext.getWorkspace().openWorkspace(dir)
                                mainWindow.editor.updateNavigation(null)
                                refreshRoot()
                                mainWindow.titleBar.updateVcsDisplay()
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
