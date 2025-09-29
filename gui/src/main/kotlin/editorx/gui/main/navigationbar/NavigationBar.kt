package editorx.gui.main.navigationbar

import editorx.gui.main.MainWindow
import editorx.gui.main.explorer.Explorer
import editorx.filetype.FileTypeRegistry
import editorx.gui.main.explorer.ExplorerIcons
import editorx.util.IconUtil
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.Icon
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * 面包屑导航栏
 */
class NavigationBar(private val mainWindow: MainWindow) : JPanel() {
    private val crumbs = mutableListOf<Crumb>()

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        border = EmptyBorder(0, 4, 0, 12)
    }

    fun update(currentFile: File?) {
        crumbs.clear()
        crumbs += buildCrumbs(mainWindow.guiControl.workspace.getWorkspaceRoot(), currentFile)

        removeAll()
        if (crumbs.isEmpty()) {
            add(JLabel("未打开文件").apply { font = font.deriveFont(Font.PLAIN, 12f) })
        } else {
            crumbs.forEachIndexed { index, crumb ->
                add(createCrumbComponent(crumb))
                if (index != crumbs.lastIndex) {
                    add(JLabel("  >  ").apply { font = font.deriveFont(Font.PLAIN, 12f) })
                }
            }
        }
        revalidate()
        repaint()
    }

    private fun buildCrumbs(workspaceRoot: File?, currentFile: File?): List<Crumb> {
        val result = mutableListOf<Crumb>()
        if (workspaceRoot != null) {
            val projectName = workspaceRoot.name.ifEmpty { workspaceRoot.absolutePath }
            result += Crumb(projectName, workspaceRoot, true)
            if (currentFile != null) {
                val rootPath = workspaceRoot.toPath()
                val filePath = currentFile.toPath()
                if (filePath.startsWith(rootPath)) {
                    try {
                        var cumulative = workspaceRoot
                        rootPath.relativize(filePath).forEach { part ->
                            cumulative = File(cumulative, part.toString())
                            result += Crumb(part.toString(), cumulative, false)
                        }
                        return result
                    } catch (_: Exception) {
                        // fallback below
                    }
                }
                appendAbsoluteSegments(result, currentFile)
            }
            return result
        }
        if (currentFile != null) {
            appendAbsoluteSegments(result, currentFile)
        }
        return result
    }

    private fun appendAbsoluteSegments(target: MutableList<Crumb>, file: File) {
        val parts = file.absolutePath.split(File.separatorChar).filter { it.isNotEmpty() }
        var cumulative = if (file.isAbsolute) File(File.separator) else File("")
        parts.forEach { part ->
            cumulative = File(cumulative, part)
            target += Crumb(part, cumulative, false)
        }
    }

    private fun createCrumbComponent(crumb: Crumb): JComponent {
        val container = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = crumb.file?.absolutePath
            border = EmptyBorder(2, 4, 2, 4) // 添加内边距
        }
        val iconLabel = JLabel(resolveIcon(crumb)).apply {
            border = EmptyBorder(0, 0, 0, 4)
            verticalAlignment = SwingConstants.CENTER
        }
        container.add(iconLabel)
        container.add(JLabel(crumb.display).apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = Color(99, 99, 99)
            verticalAlignment = SwingConstants.CENTER
        })
        container.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                container.isOpaque = true
                container.background = Color(200, 200, 200, 0xef) // 半透明浅灰色
                container.repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                container.isOpaque = false
                container.repaint()
            }

            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) handleClick(crumb)
            }

            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) showMenu(crumb, e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) showMenu(crumb, e)
            }
        })
        return container
    }

    private fun resolveIcon(crumb: Crumb): Icon? {
        val file = crumb.file ?: return ExplorerIcons.Folder?.let { IconUtil.resizeIcon(it, 16, 16) }
        return if (file.isDirectory) {
//            ExplorerIcons.Folder?.let { IconUtil.resizeIcon(it, 16, 16) }
            return null
        } else {
            val fileTypeIcon = FileTypeRegistry.getFileTypeByFileName(file.name)?.getIcon()
            val resized = fileTypeIcon?.let { IconUtil.resizeIcon(it, 14, 14) }
            resized ?: ExplorerIcons.AnyType?.let { IconUtil.resizeIcon(it, 14, 14) }
        }
    }

    private fun handleClick(crumb: Crumb) {
        val file = crumb.file ?: return
        val explorer = mainWindow.sideBar.getView("explorer") as? Explorer
        if (file.isDirectory) {
            explorer?.focusFileInTree(file)
            mainWindow.sideBar.showView("explorer")
        } else {
            mainWindow.editor.openFile(file)
        }
    }

    private fun showMenu(crumb: Crumb, e: MouseEvent) {
        val file = crumb.file ?: return
        val menu = JPopupMenu()
        if (file.isDirectory) {
            menu.add(JMenuItem("在资源管理器中打开").apply {
                addActionListener { reveal(file) }
            })
            menu.add(JMenuItem("设为工作区根目录").apply {
                addActionListener {
                    mainWindow.guiControl.workspace.openWorkspace(file)
                    update(null)
                    (mainWindow.sideBar.getView("explorer") as? Explorer)?.refreshRoot()
                }
            })
            menu.add(JMenuItem("在侧栏中选中").apply {
                addActionListener {
                    (mainWindow.sideBar.getView("explorer") as? Explorer)?.focusFileInTree(file)
                    mainWindow.sideBar.showView("explorer")
                }
            })
        } else {
            menu.add(JMenuItem("打开文件").apply {
                addActionListener { mainWindow.editor.openFile(file) }
            })
            menu.add(JMenuItem("在资源管理器中显示").apply {
                addActionListener { reveal(file) }
            })
            menu.add(JMenuItem("在侧栏中选中").apply {
                addActionListener {
                    (mainWindow.sideBar.getView("explorer") as? Explorer)?.focusFileInTree(file)
                    mainWindow.sideBar.showView("explorer")
                }
            })
        }
        menu.add(JMenuItem("复制路径").apply { addActionListener { copyPath(file) } })
        menu.show(e.component, e.x, e.y)
    }

    private fun reveal(file: File) {
        try {
            if (!file.exists()) {
                JOptionPane.showMessageDialog(this, "路径不存在: ${file.absolutePath}", "提示", JOptionPane.INFORMATION_MESSAGE)
                return
            }
            val desktop = java.awt.Desktop.getDesktop()
            if (file.isDirectory) desktop.open(file) else desktop.open(file.parentFile)
        } catch (ex: Exception) {
            JOptionPane.showMessageDialog(this, "无法在系统中打开: ${ex.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun copyPath(file: File) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(file.absolutePath), null)
            mainWindow.statusBar.setMessage("已复制路径: ${file.name}")
        } catch (ex: Exception) {
            JOptionPane.showMessageDialog(this, "复制路径失败: ${ex.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }

    private data class Crumb(val display: String, val file: File?, val isWorkspaceRoot: Boolean)
}
