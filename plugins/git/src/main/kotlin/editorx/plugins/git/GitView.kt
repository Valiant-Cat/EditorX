package editorx.plugins.git

import editorx.core.plugin.gui.PluginGuiProvider
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Git 工具窗口视图
 * 参考 IntelliJ IDEA/VSCode 的 Git 工具窗口设计
 */
class GitView(private val guiContext: PluginGuiProvider) : JPanel(BorderLayout()) {
    
    // 变更内容相关组件
    private val commitMessageField = JTextField()
    private val commitButton = JButton("提交")
    private val stagedChangesList = JList<GitFileItem>()
    private val unstagedChangesList = JList<GitFileItem>()
    
    // 提交历史相关组件
    private val commitHistoryList = JList<CommitItem>()
    
    // 数据模型
    private val stagedChangesModel = DefaultListModel<GitFileItem>()
    private val unstagedChangesModel = DefaultListModel<GitFileItem>()
    private val commitHistoryModel = DefaultListModel<CommitItem>()
    
    init {
        stagedChangesList.model = stagedChangesModel
        unstagedChangesList.model = unstagedChangesModel
        commitHistoryList.model = commitHistoryModel
        
        buildUI()
        loadGitInfo()
    }
    
    private fun buildUI() {
        // 使用垂直分割：上半部分显示变更内容，下半部分显示提交历史
        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            dividerLocation = 400  // 默认上半部分高度
            dividerSize = 8
            isOneTouchExpandable = true
        }
        
        // 上半部分：变更内容（Source Control）
        val changesPanel = createChangesPanel()
        mainSplit.topComponent = changesPanel
        
        // 下半部分：提交历史（Graph）
        val historyPanel = createHistoryPanel()
        mainSplit.bottomComponent = historyPanel
        
        add(mainSplit, BorderLayout.CENTER)
    }
    
    private fun createChangesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 顶部：提交消息输入框和提交按钮
        val commitPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 8, 8)
        }
        
        val messageLabel = JLabel("Message:").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
        }
        commitPanel.add(messageLabel, BorderLayout.NORTH)
        
        val inputPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 0, 0, 0)
        }
        
        commitMessageField.toolTipText = "输入提交信息"
        commitMessageField.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            EmptyBorder(4, 8, 4, 8)
        )
        inputPanel.add(commitMessageField, BorderLayout.CENTER)
        
        commitButton.addActionListener { performCommit() }
        inputPanel.add(commitButton, BorderLayout.EAST)
        
        commitPanel.add(inputPanel, BorderLayout.CENTER)
        panel.add(commitPanel, BorderLayout.NORTH)
        
        // 中间：使用垂直分割显示已暂存和未暂存的变更
        val changesSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            dividerLocation = 200
            dividerSize = 8
            isOneTouchExpandable = true
        }
        
        // 已暂存变更
        val stagedPanel = createStagedChangesPanel()
        changesSplit.topComponent = stagedPanel
        
        // 未暂存变更
        val unstagedPanel = createUnstagedChangesPanel()
        changesSplit.bottomComponent = unstagedPanel
        
        panel.add(changesSplit, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createStagedChangesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        val titlePanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 4, 8)
        }
        
        val title = JLabel("Staged Changes").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
        titlePanel.add(title, BorderLayout.WEST)
        
        val countLabel = JLabel("").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = Color.GRAY
        }
        titlePanel.add(countLabel, BorderLayout.EAST)
        
        // 监听模型变化更新计数
        stagedChangesModel.addListDataListener(object : javax.swing.event.ListDataListener {
            override fun contentsChanged(e: javax.swing.event.ListDataEvent) {
                val count = stagedChangesModel.size
                countLabel.text = if (count > 0 && stagedChangesModel[0].filePath != "无变更文件") "$count" else ""
            }
            override fun intervalAdded(e: javax.swing.event.ListDataEvent) {
                contentsChanged(e)
            }
            override fun intervalRemoved(e: javax.swing.event.ListDataEvent) {
                contentsChanged(e)
            }
        })
        
        panel.add(titlePanel, BorderLayout.NORTH)
        
        stagedChangesList.cellRenderer = GitFileItemRenderer()
        stagedChangesList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        panel.add(JScrollPane(stagedChangesList), BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createUnstagedChangesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        val titlePanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 4, 8)
        }
        
        val title = JLabel("Changes").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
        titlePanel.add(title, BorderLayout.WEST)
        
        val countLabel = JLabel("").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = Color.GRAY
        }
        titlePanel.add(countLabel, BorderLayout.EAST)
        
        // 监听模型变化更新计数
        unstagedChangesModel.addListDataListener(object : javax.swing.event.ListDataListener {
            override fun contentsChanged(e: javax.swing.event.ListDataEvent) {
                val count = unstagedChangesModel.size
                countLabel.text = if (count > 0 && unstagedChangesModel[0].filePath != "无变更文件") "$count" else ""
            }
            override fun intervalAdded(e: javax.swing.event.ListDataEvent) {
                contentsChanged(e)
            }
            override fun intervalRemoved(e: javax.swing.event.ListDataEvent) {
                contentsChanged(e)
            }
        })
        
        panel.add(titlePanel, BorderLayout.NORTH)
        
        unstagedChangesList.cellRenderer = GitFileItemRenderer()
        unstagedChangesList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        panel.add(JScrollPane(unstagedChangesList), BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createHistoryPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        val titlePanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 4, 8)
        }
        
        val title = JLabel("Graph").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
        titlePanel.add(title, BorderLayout.WEST)
        
        val refreshButton = JButton("刷新").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { refreshGitInfo() }
        }
        titlePanel.add(refreshButton, BorderLayout.EAST)
        
        panel.add(titlePanel, BorderLayout.NORTH)
        
        commitHistoryList.cellRenderer = CommitItemRenderer()
        commitHistoryList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        panel.add(JScrollPane(commitHistoryList), BorderLayout.CENTER)
        
        return panel
    }
    
    private fun loadGitInfo() {
        refreshGitInfo()
    }
    
    private fun refreshGitInfo() {
        val workspaceRoot = guiContext.getWorkspaceRoot() ?: return
        
        // 检查是否是 Git 仓库
        val gitDir = File(workspaceRoot, ".git")
        if (!gitDir.exists()) {
            updateUIForNonGitRepository()
            return
        }
        
        // 在后台线程中加载 Git 信息
        Thread {
            try {
                // 获取变更文件（区分已暂存和未暂存）
                val gitStatus = getGitStatus(workspaceRoot)
                
                // 获取提交历史
                val commitHistory = getCommitHistory(workspaceRoot)
                
                // 更新 UI
                SwingUtilities.invokeLater {
                    updateChangesLists(gitStatus)
                    updateCommitHistory(commitHistory)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "刷新 Git 信息失败: ${e.message}",
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
    
    private fun updateUIForNonGitRepository() {
        stagedChangesModel.clear()
        unstagedChangesModel.clear()
        commitHistoryModel.clear()
        
        stagedChangesModel.addElement(GitFileItem("当前目录不是 Git 仓库", "", null))
        unstagedChangesModel.addElement(GitFileItem("当前目录不是 Git 仓库", "", null))
        commitHistoryModel.addElement(CommitItem("当前目录不是 Git 仓库", ""))
    }
    
    private fun getGitStatus(workspaceRoot: File): GitStatus {
        return try {
            val process = ProcessBuilder("git", "status", "--porcelain")
                .directory(workspaceRoot)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            
            if (process.exitValue() == 0) {
                parseGitStatus(output, workspaceRoot)
            } else {
                GitStatus(emptyList(), emptyList())
            }
        } catch (e: Exception) {
            GitStatus(emptyList(), emptyList())
        }
    }
    
    private fun parseGitStatus(output: String, workspaceRoot: File): GitStatus {
        val staged = mutableListOf<GitFileItem>()
        val unstaged = mutableListOf<GitFileItem>()
        
        output.lines().filter { it.isNotBlank() }.forEach { line ->
            // git status --porcelain 格式: XY filename
            // X = staged status, Y = unstaged status
            if (line.length >= 3) {
                val stagedStatus = line[0]
                val unstagedStatus = line[1]
                val filePath = line.substring(3).trim()
                val file = File(workspaceRoot, filePath)
                
                if (stagedStatus != ' ') {
                    // 已暂存
                    staged.add(GitFileItem(filePath, getStatusText(stagedStatus), file))
                }
                
                if (unstagedStatus != ' ') {
                    // 未暂存
                    unstaged.add(GitFileItem(filePath, getStatusText(unstagedStatus), file))
                }
            }
        }
        
        return GitStatus(staged, unstaged)
    }
    
    private fun getStatusText(status: Char): String {
        return when (status) {
            'M' -> "M"  // Modified
            'A' -> "A"  // Added
            'D' -> "D"  // Deleted
            'R' -> "R"  // Renamed
            'C' -> "C"  // Copied
            'U' -> "U"  // Unmerged
            '?' -> "U"  // Untracked (显示为 U)
            '!' -> "!"  // Ignored
            else -> status.toString()
        }
    }
    
    private fun getCommitHistory(workspaceRoot: File, limit: Int = 50): List<CommitItem> {
        return try {
            val process = ProcessBuilder("git", "log", "--oneline", "-n", limit.toString())
                .directory(workspaceRoot)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            
            if (process.exitValue() == 0) {
                output.lines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        // 格式: hash message
                        val spaceIndex = line.indexOf(' ')
                        if (spaceIndex > 0) {
                            val hash = line.substring(0, spaceIndex)
                            val message = line.substring(spaceIndex + 1)
                            CommitItem(message, hash)
                        } else {
                            CommitItem(line, "")
                        }
                    }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun updateChangesLists(gitStatus: GitStatus) {
        stagedChangesModel.clear()
        unstagedChangesModel.clear()
        
        if (gitStatus.staged.isEmpty() && gitStatus.unstaged.isEmpty()) {
            stagedChangesModel.addElement(GitFileItem("无变更文件", "", null))
            unstagedChangesModel.addElement(GitFileItem("无变更文件", "", null))
        } else {
            gitStatus.staged.forEach { stagedChangesModel.addElement(it) }
            gitStatus.unstaged.forEach { unstagedChangesModel.addElement(it) }
        }
    }
    
    private fun updateCommitHistory(commitHistory: List<CommitItem>) {
        commitHistoryModel.clear()
        if (commitHistory.isEmpty()) {
            commitHistoryModel.addElement(CommitItem("暂无提交历史", ""))
        } else {
            commitHistory.forEach { commitHistoryModel.addElement(it) }
        }
    }
    
    private fun performCommit() {
        val workspaceRoot = guiContext.getWorkspaceRoot() ?: return
        
        // 检查是否是 Git 仓库
        val gitDir = File(workspaceRoot, ".git")
        if (!gitDir.exists()) {
            JOptionPane.showMessageDialog(
                this,
                "当前目录不是 Git 仓库",
                "提示",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        
        val message = commitMessageField.text.trim()
        if (message.isBlank()) {
            JOptionPane.showMessageDialog(
                this,
                "请输入提交信息",
                "提示",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        
        // 检查是否有已暂存的变更
        if (stagedChangesModel.isEmpty() || 
            (stagedChangesModel.size == 1 && stagedChangesModel[0].filePath == "无变更文件")) {
            JOptionPane.showMessageDialog(
                this,
                "没有需要提交的变更",
                "提示",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        
        Thread {
            try {
                // 执行提交（已暂存的文件）
                val commitProcess = ProcessBuilder("git", "commit", "-m", message)
                    .directory(workspaceRoot)
                    .redirectErrorStream(true)
                    .start()
                val output = commitProcess.inputStream.bufferedReader().use { it.readText() }
                val exitCode = commitProcess.waitFor()
                
                SwingUtilities.invokeLater {
                    if (exitCode == 0) {
                        commitMessageField.text = ""
                        refreshGitInfo()
                        JOptionPane.showMessageDialog(
                            this,
                            "提交成功",
                            "成功",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    } else {
                        JOptionPane.showMessageDialog(
                            this,
                            "提交失败: $output",
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "提交时出错: ${e.message}",
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
    
    // 数据类
    data class GitStatus(
        val staged: List<GitFileItem>,
        val unstaged: List<GitFileItem>
    )
    
    data class GitFileItem(
        val filePath: String,
        val status: String,
        val file: File?
    )
    
    data class CommitItem(
        val message: String,
        val hash: String
    )
    
    // 自定义渲染器
    private class GitFileItemRenderer : ListCellRenderer<GitFileItem> {
        private val renderer = DefaultListCellRenderer()
        
        override fun getListCellRendererComponent(
            list: JList<out GitFileItem>,
            value: GitFileItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val component = renderer.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus
            ) as JLabel
            
            if (value != null) {
                val displayText = if (value.status.isNotBlank()) {
                    "${value.status} ${value.filePath}"
                } else {
                    value.filePath
                }
                component.text = displayText
            }
            
            return component
        }
    }
    
    private class CommitItemRenderer : ListCellRenderer<CommitItem> {
        private val renderer = DefaultListCellRenderer()
        
        override fun getListCellRendererComponent(
            list: JList<out CommitItem>,
            value: CommitItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val component = renderer.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus
            ) as JLabel
            
            if (value != null) {
                component.text = value.message
            }
            
            return component
        }
    }
}

