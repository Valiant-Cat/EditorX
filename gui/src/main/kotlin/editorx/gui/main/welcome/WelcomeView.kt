package editorx.gui.main.welcome

import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import editorx.gui.core.ThemeManager
import editorx.gui.main.MainWindow
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

class WelcomeView(private val mainWindow: MainWindow) : JPanel() {
    private var centerPanel: JPanel? = null
    
    init {
        layout = BorderLayout()
        background = ThemeManager.currentTheme.editorBackground
        updateTheme()
        
        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }
        
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
        val workspaces = mainWindow.guiContext.workspace.recentWorkspaces()
        println("Debug: recentWorkspaces count = ${workspaces.size}")
        workspaces.forEachIndexed { idx, file ->
            println("  [$idx] ${file.absolutePath} (exists=${file.exists()}, isDirectory=${file.isDirectory})")
        }
    }
    
    private fun updateTheme() {
        background = ThemeManager.currentTheme.editorBackground
        revalidate()
        repaint()
    }
    
    private fun createCenterPanel(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            background = ThemeManager.currentTheme.editorBackground
            border = EmptyBorder(0, 0, 0, 0)
        }
        
        // 顶部弹性空间
        val topSpacer = JPanel().apply {
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
            background = ThemeManager.currentTheme.editorBackground
            border = EmptyBorder(0, 0, 0, 0)
            alignmentX = CENTER_ALIGNMENT
        }
        
        // 新建文件 按钮
        val newFileBtn = createActionButton(
            icon = IconLoader.getIcon(IconRef("icons/common/addFile.svg"), 24),
            text = "新建文件",
            onClick = { newFile() }
        )
        panel.add(newFileBtn)
        
        panel.add(Box.createHorizontalStrut(24))
        
        // 打开文件 按钮
        val openFileBtn = createActionButton(
            icon = IconLoader.getIcon(IconRef("icons/common/anyType.svg"), 24),
            text = "打开文件",
            onClick = { openFile() }
        )
        panel.add(openFileBtn)
        
        panel.add(Box.createHorizontalStrut(24))
        
        // 打开项目 按钮
        val openProjectBtn = createActionButton(
            icon = IconLoader.getIcon(IconRef("icons/common/folder.svg"), 24),
            text = "打开项目",
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
            background = Color(0xF2, 0xF2, 0xF2) // #f2f2f2
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
            
            // 悬停效果
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    background = Color(0xE0, 0xE0, 0xE0) // 稍深的灰色
                    repaint()
                }
                
                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    background = Color(0xF2, 0xF2, 0xF2) // 恢复 #f2f2f2
                    repaint()
                }
            })
        }
        return button
    }
    
    private fun createRecentProjectsPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            background = ThemeManager.currentTheme.editorBackground
            border = EmptyBorder(0, 0, 0, 0)
            preferredSize = Dimension(410, 300)
            minimumSize = Dimension(410, 200)
            maximumSize = Dimension(410, Int.MAX_VALUE)
        }
        
        val headerPanel = JPanel(BorderLayout()).apply {
            background = ThemeManager.currentTheme.editorBackground
            border = EmptyBorder(0, 0, 20, 0)
        }
        
        val titleLabel = JLabel("Recent projects").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            foreground = ThemeManager.currentTheme.onSurface
        }
        headerPanel.add(titleLabel, BorderLayout.WEST)
        
        val recentWorkspaces = mainWindow.guiContext.workspace.recentWorkspaces()
        // Debug: 打印最近项目信息
        if (recentWorkspaces.isNotEmpty()) {
            println("WelcomeView: Found ${recentWorkspaces.size} recent workspaces")
            recentWorkspaces.forEach { println("  - ${it.absolutePath}") }
        } else {
            println("WelcomeView: No recent workspaces found")
        }
        val viewAllLabel = JLabel("View all (${recentWorkspaces.size})").apply {
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
            background = ThemeManager.currentTheme.editorBackground
        }
        
        if (projects.isEmpty()) {
            val emptyLabel = JLabel("No recent projects").apply {
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
            background = ThemeManager.currentTheme.editorBackground
            border = EmptyBorder(8, 12, 8, 12)
            preferredSize = Dimension(0, 48)
            minimumSize = Dimension(0, 48)
            maximumSize = Dimension(Int.MAX_VALUE, 48)
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    background = Color(ThemeManager.currentTheme.editorBackground.rgb and 0xFFFFFF or 0x10000000.toInt())
                    repaint()
                }
                
                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    background = ThemeManager.currentTheme.editorBackground
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
        val settings = mainWindow.guiContext.settings
        val workspacePath = workspace.absolutePath
        
        // 获取所有最近项目，移除指定的项目
        val allWorkspaces = mainWindow.guiContext.workspace.recentWorkspaces().toMutableList()
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
                mainWindow.editor.openFile(selectedFile)
                mainWindow.guiContext.workspace.addRecentFile(selectedFile)
                mainWindow.editor.showEditorContent()
            }
        }
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
        mainWindow.guiContext.workspace.openWorkspace(workspace)
        mainWindow.guiContext.workspace.addRecentWorkspace(workspace)
        (mainWindow.sideBar.getView("explorer") as? editorx.gui.main.explorer.Explorer)?.refreshRoot()
        mainWindow.toolBar.updateVcsDisplay()
        // 自动打开资源管理器
        mainWindow.sideBar.showView("explorer")
        mainWindow.editor.showEditorContent()
    }
}

