package editorx.gui.main.editor

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import editorx.gui.theme.ThemeManager
import editorx.gui.main.MainWindow
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

class WelcomeView(private val mainWindow: MainWindow) : JPanel() {
    private var centerPanel: JPanel? = null

    init {
        layout = BorderLayout()
        isOpaque = false // 透明背景
        updateTheme()

        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }

        // 安装拖放支持
        installFileDropTarget()

        refreshContent()
    }

    fun refreshContent() {
        removeAll()
        centerPanel = createCenterPanel()
        add(centerPanel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun debugRecentWorkspaces() {
        val workspaces = mainWindow.guiContext.getWorkspace().recentWorkspaces()
        println("Debug: recentWorkspaces count = ${workspaces.size}")
        workspaces.forEachIndexed { idx, file ->
            println("  [$idx] ${file.absolutePath} (exists=${file.exists()}, isDirectory=${file.isDirectory})")
        }
    }

    private fun updateTheme() {
        // 保持透明背景，不设置背景色
        // 刷新内容以更新按钮的背景颜色
        refreshContent()
    }

    private fun createCenterPanel(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            isOpaque = false // 透明背景
            border = EmptyBorder(0, 0, 0, 0)
        }

        // 顶部弹性空间
        val topSpacer = JPanel().apply {
            isOpaque = false // 透明背景
            preferredSize = Dimension(0, 0)
            minimumSize = Dimension(0, 0)
        }
        val topGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        }
        panel.add(topSpacer, topGbc)

        // 操作按钮区域
        val actionsPanel = createActionsPanel()
        val actionsGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 1
            weightx = 1.0
            weighty = 0.0
            anchor = GridBagConstraints.CENTER
            insets = java.awt.Insets(0, 0, 40, 0)
        }
        panel.add(actionsPanel, actionsGbc)

        // Recent projects 区域
        val recentProjectsPanel = createRecentProjectsPanel()
        val projectsGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 2
            weightx = 1.0
            weighty = 0.0
            fill = GridBagConstraints.NONE
            anchor = GridBagConstraints.CENTER
            insets = java.awt.Insets(0, 0, 0, 0)
        }
        panel.add(recentProjectsPanel, projectsGbc)

        // 底部弹性空间
        val bottomSpacer = JPanel().apply {
            isOpaque = false // 透明背景
            preferredSize = Dimension(0, 0)
            minimumSize = Dimension(0, 0)
        }
        val bottomGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 3
            weightx = 1.0
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        }
        panel.add(bottomSpacer, bottomGbc)

        return panel
    }

    private fun createActionsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false // 透明背景
            border = EmptyBorder(0, 0, 0, 0)
            alignmentX = CENTER_ALIGNMENT
        }

        // 新建文件 按钮
        val newFileBtn = createActionButton(
            icon = IconLoader.getIcon(
                IconRef("icons/common/addFile.svg"), 
                24,
                adaptToTheme = true,
                getThemeColor = { ThemeManager.currentTheme.onSurface }
            ),
            text = I18n.translate(I18nKeys.Welcome.NEW_FILE),
            onClick = { newFile() }
        )
        panel.add(newFileBtn)

        panel.add(Box.createHorizontalStrut(24))

        // 打开文件 按钮
        val openFileBtn = createActionButton(
            icon = IconLoader.getIcon(
                IconRef("icons/common/anyType.svg"), 
                24,
                adaptToTheme = false,
                getThemeColor = { ThemeManager.currentTheme.onSurface }
            ),
            text = I18n.translate(I18nKeys.Welcome.OPEN_FILE),
            onClick = { openFile() }
        )
        panel.add(openFileBtn)

        panel.add(Box.createHorizontalStrut(24))

        // 打开项目 按钮
        val openProjectBtn = createActionButton(
            icon = IconLoader.getIcon(
                IconRef("icons/common/folder.svg"), 
                24,
                adaptToTheme = false,
                getThemeColor = { ThemeManager.currentTheme.onSurface }
            ),
            text = I18n.translate(I18nKeys.Welcome.OPEN_PROJECT),
            onClick = { openProject() }
        )
        panel.add(openProjectBtn)

        return panel
    }

    private fun createActionButton(icon: Icon?, text: String, onClick: () -> Unit): JButton {
        val button = object : JButton() {
            override fun paintComponent(g: java.awt.Graphics) {
                val g2 = g.create() as java.awt.Graphics2D
                try {
                    g2.setRenderingHint(
                        java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                    )
                    // 绘制圆角矩形背景
                    g2.color = background
                    g2.fillRoundRect(0, 0, width, height, 16, 16)
                } finally {
                    g2.dispose()
                }
                // 调用 super.paintComponent 绘制子组件（图标和文字）
                super.paintComponent(g)
            }
        }.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false // 设置为 false，因为我们自己绘制背景
            isContentAreaFilled = false
            isBorderPainted = false
            background = ThemeManager.currentTheme.cardBackground
            preferredSize = Dimension(120, 80)
            maximumSize = Dimension(120, 80)
            border = EmptyBorder(12, 16, 12, 16)

            // 图标
            val iconLabel = JLabel(icon).apply {
                horizontalAlignment = SwingConstants.LEFT
                alignmentX = LEFT_ALIGNMENT
            }
            add(iconLabel)

            add(Box.createVerticalStrut(8))

            // 文本
            val textLabel = JLabel(text).apply {
                font = font.deriveFont(Font.PLAIN, 13f)
                foreground = ThemeManager.currentTheme.onSurface
                horizontalAlignment = SwingConstants.LEFT
                alignmentX = LEFT_ALIGNMENT
            }
            add(textLabel)

            addActionListener { onClick() }

            // 悬停效果：使用主题的 surfaceVariant 作为悬停背景
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    background = ThemeManager.currentTheme.surfaceVariant
                    repaint()
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    background = ThemeManager.currentTheme.cardBackground
                    repaint()
                }
            })
        }
        return button
    }

    private fun createRecentProjectsPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false // 透明背景
            border = EmptyBorder(0, 0, 0, 0)
            preferredSize = Dimension(410, 300)
            minimumSize = Dimension(410, 200)
            maximumSize = Dimension(410, Int.MAX_VALUE)
        }

        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false // 透明背景
            border = EmptyBorder(0, 0, 20, 0)
        }

        val titleLabel = JLabel(I18n.translate(I18nKeys.Welcome.RECENT_PROJECTS)).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            foreground = ThemeManager.currentTheme.onSurface
        }
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val recentWorkspaces = mainWindow.guiContext.getWorkspace().recentWorkspaces()
        // Debug: 打印最近项目信息
        if (recentWorkspaces.isNotEmpty()) {
            println("WelcomeView: Found ${recentWorkspaces.size} recent workspaces")
            recentWorkspaces.forEach { println("  - ${it.absolutePath}") }
        } else {
            println("WelcomeView: No recent workspaces found")
        }
        val viewAllLabel = JLabel("${I18n.translate(I18nKeys.Welcome.VIEW_ALL)} (${recentWorkspaces.size})").apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = Color(0x6C707E)
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    // TODO: 显示所有项目对话框
                }
            })
        }
        headerPanel.add(viewAllLabel, BorderLayout.EAST)

        panel.add(headerPanel, BorderLayout.NORTH)

        // 项目列表
        val projectsList = createProjectsList(recentWorkspaces)
        panel.add(projectsList, BorderLayout.CENTER)

        return panel
    }

    private fun createProjectsList(projects: List<File>): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false // 透明背景
        }

        if (projects.isEmpty()) {
            val emptyLabel = JLabel(I18n.translate(I18nKeys.Welcome.NO_RECENT_PROJECTS)).apply {
                font = font.deriveFont(Font.PLAIN, 12f)
                foreground = Color(0x6C707E)
                alignmentX = CENTER_ALIGNMENT
            }
            panel.add(Box.createVerticalGlue())
            panel.add(emptyLabel)
            panel.add(Box.createVerticalGlue())
        } else {
            projects.take(5).forEach { project ->
                val projectItem = createProjectItem(project)
                panel.add(projectItem)
                panel.add(Box.createVerticalStrut(8))
            }
        }

        return panel
    }

    private fun createProjectItem(project: File): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false // 透明背景
            border = EmptyBorder(8, 12, 8, 12)
            preferredSize = Dimension(0, 48)
            minimumSize = Dimension(0, 48)
            maximumSize = Dimension(Int.MAX_VALUE, 48)
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    // 悬停时显示半透明背景
                    isOpaque = true
                    background =
                        Color(ThemeManager.currentTheme.editorBackground.rgb and 0xFFFFFF or 0x10000000.toInt())
                    repaint()
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    // 恢复透明背景
                    isOpaque = false
                    repaint()
                }

                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    // 如果点击的不是删除按钮，打开项目
                    if (e.source !is JButton && e.source !is JLabel) {
                        openWorkspace(project)
                    }
                }
            })
        }

        // 左侧：项目信息（两行显示）
        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = Color(0, 0, 0, 0) // 透明
            alignmentX = LEFT_ALIGNMENT
            border = EmptyBorder(0, 0, 0, 0)
        }

        val nameLabel = JLabel(project.name).apply {
            font = font.deriveFont(Font.PLAIN, 14f)
            foreground = ThemeManager.currentTheme.onSurface
            alignmentX = LEFT_ALIGNMENT
            border = EmptyBorder(0, 0, 0, 0)
        }
        infoPanel.add(nameLabel)

        infoPanel.add(Box.createVerticalStrut(2))

        val pathLabel = JLabel(project.absolutePath).apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = Color(0x6C707E)
            alignmentX = LEFT_ALIGNMENT
            border = EmptyBorder(0, 0, 0, 0)
        }
        infoPanel.add(pathLabel)

        panel.add(infoPanel, BorderLayout.CENTER)

        // 右侧：删除按钮容器（包含间距和按钮）
        val deleteButtonContainer = JPanel(BorderLayout()).apply {
            isOpaque = false
            preferredSize = Dimension(40, 24)
            border = EmptyBorder(0, 12, 0, 0) // 左侧间距
        }

        val deleteButton = JButton().apply {
            icon = IconLoader.getIcon(IconRef("icons/common/close.svg"), 24)
            isOpaque = true
            isContentAreaFilled = true
            isBorderPainted = false
            preferredSize = Dimension(24, 24)
            toolTipText = "删除"
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            background = Color(0, 0, 0, 0) // 初始透明背景

            addActionListener {
                val result = JOptionPane.showConfirmDialog(
                    mainWindow,
                    "确定要从最近项目中删除 \"${project.name}\" 吗？",
                    "删除最近项目",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                )
                if (result == JOptionPane.YES_OPTION) {
                    removeRecentWorkspace(project)
                }
            }

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    background = Color(0xE0, 0xE0, 0xE0, 0x80)
                    repaint()
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    background = Color(0, 0, 0, 0)
                    repaint()
                }
            })
        }
        deleteButtonContainer.add(deleteButton, BorderLayout.CENTER)
        panel.add(deleteButtonContainer, BorderLayout.EAST)

        return panel
    }

    private fun removeRecentWorkspace(workspace: File) {
        val settings = mainWindow.guiContext.getSettings()
        val workspacePath = workspace.absolutePath

        // 获取所有最近项目，移除指定的项目
        val allWorkspaces = mainWindow.guiContext.getWorkspace().recentWorkspaces().toMutableList()
        allWorkspaces.removeAll { it.absolutePath == workspacePath }

        // 清除所有旧的键
        settings.keys("workspaces.recent.").forEach { settings.remove(it) }

        // 重新保存剩余的项目
        allWorkspaces.forEachIndexed { idx, f ->
            settings.put("workspaces.recent.$idx", f.absolutePath)
        }
        settings.sync()

        refreshContent() // 刷新显示
    }

    private fun openFile() {
        val fileDialog = java.awt.FileDialog(mainWindow, "选择文件", java.awt.FileDialog.LOAD).apply {
            isMultipleMode = false
        }
        fileDialog.isVisible = true

        val fileName = fileDialog.file
        val dir = fileDialog.directory

        if (fileName != null && dir != null) {
            val selectedFile = File(dir, fileName)
            if (selectedFile.isFile && selectedFile.canRead()) {
                handleOpenFile(selectedFile)
            }
        }
    }

    /**
     * 处理打开文件的逻辑（可被 openFile 和拖拽使用）
     */
    private fun handleOpenFile(selectedFile: File) {
        if (!selectedFile.isFile || !selectedFile.canRead()) {
            return
        }

        // 直接调用 editor.openFile，让它统一处理文件处理器检查
        // 这样可以避免重复调用文件处理器
        mainWindow.editor.openFile(selectedFile)
        mainWindow.guiContext.getWorkspace().addRecentFile(selectedFile)
        mainWindow.editor.showEditorContent()
    }

    private fun openProject() {
        val fileDialog = java.awt.FileDialog(mainWindow, "选择项目文件夹", java.awt.FileDialog.LOAD).apply {
            isMultipleMode = false
            // 在 macOS 上，设置系统属性以使用文件夹选择模式
            if (System.getProperty("os.name").lowercase().contains("mac")) {
                System.setProperty("apple.awt.fileDialogForDirectories", "true")
            }
        }
        fileDialog.isVisible = true

        val selectedDir = fileDialog.directory?.let { dir ->
            val fileName = fileDialog.file
            if (fileName != null) {
                File(dir, fileName)
            } else {
                File(dir)
            }
        }

        // 恢复系统属性
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }

        if (selectedDir != null && selectedDir.isDirectory) {
            openWorkspace(selectedDir)
        }
    }

    private fun newFile() {
        mainWindow.editor.newUntitledFile()
        mainWindow.editor.showEditorContent()
    }

    private fun openWorkspace(workspace: File) {
        mainWindow.guiContext.getWorkspace().openWorkspace(workspace)
        mainWindow.guiContext.getWorkspace().addRecentWorkspace(workspace)
        (mainWindow.sideBar.getView("explorer") as? editorx.gui.main.explorer.Explorer)?.refreshRoot()
        mainWindow.titleBar.updateVcsDisplay()
        // 自动打开资源管理器
        mainWindow.sideBar.showView("explorer")
        mainWindow.editor.showEditorContent()
    }

    // DnD: 支持拖放目录到欢迎界面打开工作区
    private fun installFileDropTarget() {
        try {
            val listener = object : DropTargetListener {
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

                        if (files.isEmpty()) {
                            dtde.dropComplete(false)
                            return
                        }

                        // 优先处理文件夹（与 openProject 相同效果）
                        val dir = files.firstOrNull { it.isDirectory }
                        if (dir != null) {
                            openWorkspace(dir)
                            mainWindow.statusBar.setMessage("已打开文件夹: ${dir.name}")
                            dtde.dropComplete(true)
                            return
                        }

                        // 处理文件（与 openFile 相同效果）
                        val file = files.firstOrNull { it.isFile && it.canRead() }
                        if (file != null) {
                            handleOpenFile(file)
                            dtde.dropComplete(true)
                            return
                        }

                        // 如果没有可处理的文件或文件夹
                        dtde.dropComplete(false)
                    } catch (e: Exception) {
                        dtde.dropComplete(false)
                        JOptionPane.showMessageDialog(
                            mainWindow,
                            "拖放操作失败: ${e.message}",
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
            DropTarget(this, DnDConstants.ACTION_COPY, listener, true)
        } catch (e: Exception) {
            println("WelcomeView: 无法安装拖放目标: ${e.message}")
        }
    }

    private fun isDragAcceptable(e: DropTargetDragEvent): Boolean {
        val flavors = e.transferable.transferDataFlavors
        val hasList = flavors.any { it.isFlavorJavaFileListType }
        if (!hasList) return false

        // 检查是否包含文件或文件夹
        try {
            val files = e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
            if (files != null && files.isNotEmpty()) {
                return files.any { it.isDirectory || (it.isFile && it.canRead()) }
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

        // 检查是否包含文件或文件夹
        try {
            val files = e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
            if (files != null && files.isNotEmpty()) {
                return files.any { it.isDirectory || (it.isFile && it.canRead()) }
            }
        } catch (e: Exception) {
            // 忽略异常，继续检查
        }

        return true
    }
}

