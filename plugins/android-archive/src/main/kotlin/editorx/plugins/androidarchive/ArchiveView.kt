package editorx.plugins.androidarchive

import editorx.gui.main.MainWindow
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * 压缩包浏览器视图
 * 用于显示 XAPK、AAB、AAR 等压缩包文件的内容
 */
class ArchiveView(private val archiveFile: File, private val mainWindow: MainWindow) : JPanel(BorderLayout()) {
    
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("加载中..."))
    private val fileTree = JTree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = ArchiveTreeCellRenderer()
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val entry = node.userObject as? ZipEntry ?: return
                    openEntry(entry)
                }
            }
        })
    }
    
    private val infoLabel = JLabel().apply {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        font = font.deriveFont(Font.PLAIN, 11f)
        foreground = Color.GRAY
    }
    
    init {
        buildUI()
        loadArchive()
    }
    
    private fun buildUI() {
        val scrollPane = JScrollPane(fileTree).apply {
            border = BorderFactory.createEmptyBorder()
        }
        
        add(scrollPane, BorderLayout.CENTER)
        add(infoLabel, BorderLayout.SOUTH)
    }
    
    private fun loadArchive() {
        Thread {
            try {
                val zipFile = ZipFile(archiveFile)
                val rootNode = DefaultMutableTreeNode(archiveFile.name)
                
                val entries = zipFile.entries().toList()
                val entryMap = mutableMapOf<String, DefaultMutableTreeNode>()
                
                // 构建树结构
                entries.forEach { entry ->
                    val parts = entry.name.split("/").filter { it.isNotEmpty() }
                    var currentPath = ""
                    var parentNode = rootNode
                    
                    parts.forEachIndexed { index, part ->
                        currentPath += if (currentPath.isEmpty()) part else "/$part"
                        val isDirectory = index < parts.size - 1 || entry.name.endsWith("/")
                        
                        val node = entryMap.getOrPut(currentPath) {
                            val newNode = DefaultMutableTreeNode(
                                if (isDirectory) ArchiveEntry(part, true, null)
                                else ArchiveEntry(part, false, entry)
                            )
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
    }
    
    private fun openEntry(entry: ZipEntry) {
        if (entry.isDirectory) return
        
        Thread {
            try {
                val zipFile = ZipFile(archiveFile)
                val zipEntry = zipFile.getEntry(entry.name) ?: return@Thread
                
                val inputStream = zipFile.getInputStream(zipEntry)
                val content = inputStream.bufferedReader().use { it.readText() }
                zipFile.close()
                
                // 创建临时文件并打开
                val tempFile = File.createTempFile("archive_", "_${entry.name.split("/").last()}")
                tempFile.writeText(content)
                tempFile.deleteOnExit()
                
                SwingUtilities.invokeLater {
                    mainWindow.guiControl.editor.openFile(tempFile)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
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
    
    /**
     * 压缩包条目数据类
     */
    private data class ArchiveEntry(
        val name: String,
        val isDirectory: Boolean,
        val zipEntry: ZipEntry?
    )
    
    /**
     * 树节点渲染器
     */
    private class ArchiveTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            val component = super.getTreeCellRendererComponent(
                tree, value, sel, expanded, leaf, row, hasFocus
            ) as JLabel
            
            val node = value as? DefaultMutableTreeNode ?: return component
            val obj = node.userObject
            
            when (obj) {
                is ArchiveEntry -> {
                    component.text = obj.name
                    component.icon = if (obj.isDirectory) {
                        UIManager.getIcon("FileView.directoryIcon")
                    } else {
                        UIManager.getIcon("FileView.fileIcon")
                    }
                }
                is ZipEntry -> {
                    component.text = obj.name.split("/").last()
                    component.icon = UIManager.getIcon("FileView.fileIcon")
                }
                else -> {
                    component.text = obj.toString()
                }
            }
            
            return component
        }
    }
}

