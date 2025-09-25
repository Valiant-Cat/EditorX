package editorx.plugins.explorer

import editorx.gui.CachedViewProvider
import editorx.gui.ViewArea
import editorx.plugin.Plugin
import editorx.plugin.PluginContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * 内置 Explorer 插件（示例）
 */
class ExplorerPlugin : Plugin {

    private lateinit var fileTree: JTree
    private lateinit var treeModel: DefaultTreeModel
    private lateinit var rootNode: DefaultMutableTreeNode
    private lateinit var searchField: JTextField
    private lateinit var statusLabel: JLabel
    private var currentApkFile: File? = null
    private var context: PluginContext? = null

    // 插件信息改由 JAR Manifest 提供（Plugin-Name、Plugin-Description 等）

    override fun activate(context: PluginContext) {
        this.context = context

        context.addActivityBarItem(
            "icons/explorer.svg",
            object : CachedViewProvider() {
                override fun createView(): JComponent {
                    return createExplorerView()
                }

                override fun area(): ViewArea = ViewArea.PANEL
            }
        )
        println("Explorer插件已启动")
    }

    private fun createExplorerView(): JPanel {
        val panel = JPanel(BorderLayout())
        val toolbar = createToolbar()
        panel.add(toolbar, BorderLayout.NORTH)
        val treePanel = createFileTree()
        panel.add(treePanel, BorderLayout.CENTER)
        val statusPanel = createStatusPanel()
        panel.add(statusPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)

        val openApkBtn = JButton("打开APK").apply {
            addActionListener { openApkFile() }
            toolTipText = "打开APK文件"
        }

        val refreshBtn = JButton("刷新").apply {
            addActionListener { refreshTree() }
            toolTipText = "刷新文件树"
        }

        searchField = JTextField().apply {
            preferredSize = Dimension(150, 25)
            toolTipText = "搜索文件..."
        }

        val searchBtn = JButton("搜索").apply {
            addActionListener { performSearch() }
            toolTipText = "搜索文件"
        }

        toolbar.add(openApkBtn)
        toolbar.add(Box.createHorizontalStrut(5))
        toolbar.add(refreshBtn)
        toolbar.add(Box.createHorizontalStrut(10))
        toolbar.add(JLabel("搜索:"))
        toolbar.add(Box.createHorizontalStrut(5))
        toolbar.add(searchField)
        toolbar.add(Box.createHorizontalStrut(5))
        toolbar.add(searchBtn)

        return toolbar
    }

    private fun createFileTree(): JPanel {
        val panel = JPanel(BorderLayout())

        rootNode = DefaultMutableTreeNode("APK内容")
        treeModel = DefaultTreeModel(rootNode)
        fileTree = JTree(treeModel).apply {
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = FileTreeCellRenderer()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) openSelectedFile()
                }
            })
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (e.isPopupTrigger) showContextMenu(e)
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger) showContextMenu(e)
                }
            })
        }

        val scrollPane = JScrollPane(fileTree).apply { preferredSize = Dimension(300, 400) }
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    private fun createStatusPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        statusLabel = JLabel("就绪").apply { foreground = Color.GRAY }
        panel.add(statusLabel)
        panel.add(Box.createHorizontalGlue())
        return panel
    }

    private fun openApkFile() {
        val fileChooser = JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("APK Files", "apk")
            dialogTitle = "选择APK文件"
        }
        if (fileChooser.showOpenDialog(fileTree) == JFileChooser.APPROVE_OPTION) {
            val apkFile = fileChooser.selectedFile
            currentApkFile = apkFile
            loadApkContent(apkFile)
            statusLabel.text = "已加载: ${apkFile.name}"
        }
    }

    private fun loadApkContent(apkFile: File) {
        rootNode.removeAllChildren()
        val apkNode = DefaultMutableTreeNode(FileNode(apkFile.name, apkFile, true))
        rootNode.add(apkNode)
        val manifestNode = DefaultMutableTreeNode(FileNode("AndroidManifest.xml", File("AndroidManifest.xml"), false))
        apkNode.add(manifestNode)
        val classesNode = DefaultMutableTreeNode(FileNode("classes.dex", File("classes.dex"), false))
        apkNode.add(classesNode)
        val resourcesNode = DefaultMutableTreeNode(FileNode("resources.arsc", File("resources.arsc"), false))
        apkNode.add(resourcesNode)
        val assetsNode = DefaultMutableTreeNode(FileNode("assets", File("assets"), true))
        apkNode.add(assetsNode)
        val resNode = DefaultMutableTreeNode(FileNode("res", File("res"), true))
        apkNode.add(resNode)
        val layoutNode = DefaultMutableTreeNode(FileNode("layout", File("res/layout"), true))
        resNode.add(layoutNode)
        val mainActivityNode =
            DefaultMutableTreeNode(FileNode("activity_main.xml", File("res/layout/activity_main.xml"), false))
        layoutNode.add(mainActivityNode)
        val valuesNode = DefaultMutableTreeNode(FileNode("values", File("res/values"), true))
        resNode.add(valuesNode)
        val stringsNode = DefaultMutableTreeNode(FileNode("strings.xml", File("res/values/strings.xml"), false))
        valuesNode.add(stringsNode)
        val colorsNode = DefaultMutableTreeNode(FileNode("colors.xml", File("res/values/colors.xml"), false))
        valuesNode.add(colorsNode)
        fileTree.expandPath(javax.swing.tree.TreePath(apkNode.path))
        treeModel.reload()
    }

    private fun refreshTree() {
        currentApkFile?.let { loadApkContent(it) }
    }

    private fun openSelectedFile() {
        val selectedNode = fileTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val fileNode = selectedNode.userObject as? FileNode ?: return
        if (!fileNode.isDirectory) {
            val tempFile = File.createTempFile("apk_", "_${fileNode.file.name}")
            tempFile.writeText("// 这是从APK中提取的文件内容\n// 文件: ${fileNode.file.name}\n// 实际内容需要从APK中解析\n")
            context?.openFile(tempFile)
            statusLabel.text = "已打开: ${fileNode.file.name}"
        }
    }

    private fun showContextMenu(e: MouseEvent) {
        val selectedNode = fileTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val fileNode = selectedNode.userObject as? FileNode ?: return
        val popup = JPopupMenu()
        if (!fileNode.isDirectory) {
            val openItem = JMenuItem("打开").apply { addActionListener { openSelectedFile() } }
            popup.add(openItem)
            popup.addSeparator()
        }
        val copyItem = JMenuItem("复制").apply { addActionListener { copyFile(fileNode) } }
        popup.add(copyItem)
        val extractItem = JMenuItem("提取").apply { addActionListener { extractFile(fileNode) } }
        popup.add(extractItem)
        popup.show(fileTree, e.x, e.y)
    }

    private fun copyFile(fileNode: FileNode) {
        statusLabel.text = "复制文件: ${fileNode.file.name}"
    }

    private fun extractFile(fileNode: FileNode) {
        val fileChooser = JFileChooser().apply {
            dialogTitle = "选择保存位置"
            selectedFile = File(fileNode.file.name)
        }
        if (fileChooser.showSaveDialog(fileTree) == JFileChooser.APPROVE_OPTION) {
            statusLabel.text = "已提取: ${fileNode.file.name}"
        }
    }

    private fun performSearch() {
        val searchText = searchField.text.trim()
        if (searchText.isNotEmpty()) statusLabel.text = "搜索: $searchText"
    }
}

data class FileNode(
    val name: String,
    val file: File,
    val isDirectory: Boolean
) {
    override fun toString(): String = name
}

class FileTreeCellRenderer : javax.swing.tree.DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
    ): java.awt.Component {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        val node = (value as? DefaultMutableTreeNode)?.userObject as? FileNode
        if (node != null) {
            text = node.name
            icon = if (node.isDirectory) UIManager.getIcon("FileView.directoryIcon") else getFileIcon(node.file)
        }
        return this
    }

    private fun getFileIcon(file: File): Icon {
        return UIManager.getIcon("FileView.fileIcon")
    }
}
