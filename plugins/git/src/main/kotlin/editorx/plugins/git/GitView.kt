package editorx.plugins.git

import editorx.core.gui.DiffHunk
import editorx.core.gui.GuiExtension
import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import javax.swing.Timer

/**
 * Git 工具窗口视图
 * 布局参考 VSCode Source Control：Changes + Graph
 */
class GitView(private val gui: GuiExtension) : JPanel(BorderLayout()) {
    private val themeTextColor: Color = gui.getThemeTextColor()
    private val themeMutedTextColor: Color = gui.getThemeDisabledTextColor()

    private var lastOpenedDiffKey: String? = null
    private var lastOpenedDiffAtMs: Long = 0L
    @Volatile private var diffRequestId: Int = 0

    // 顶部提交相关
    private val commitMessageField = PlaceholderTextArea()
    private val commitButton = JButton("✓ Commit")

    // Changes
    private val unstagedChangesModel = DefaultListModel<GitFileItem>()
    private val stagedChangesModel = DefaultListModel<GitFileItem>()
    private val unstagedChangesList = JList(unstagedChangesModel)
    private val stagedChangesList = JList(stagedChangesModel)
    private val unstagedCountLabel = createCountBadge()
    private val stagedCountLabel = createCountBadge()

    // Graph
    private val graphModel = DefaultListModel<CommitItem>()
    private val graphList = JList(graphModel)
    private var currentBranch: String? = null
    private var graphBranchLabel: JLabel? = null

    // 折叠控制
    private val unstagedSection = CollapsibleSection(
        title = "Changes",
        countLabel = unstagedCountLabel,
        content = unstagedChangesList,
        expanded = true,
        textColor = themeTextColor,
        mutedTextColor = themeMutedTextColor,
    )
    private val stagedSection = CollapsibleSection(
        title = "Staged Changes",
        countLabel = stagedCountLabel,
        content = stagedChangesList,
        expanded = true,
        textColor = themeTextColor,
        mutedTextColor = themeMutedTextColor,
    )

    // CHANGES 和 GRAPH 的可折叠部分
    private lateinit var changesSection: CollapsibleSection
    private lateinit var graphSection: CollapsibleSection

    // 自动刷新（用于文件保存后 changes 及时更新）
    private val autoRefreshTimer: Timer
    @Volatile private var autoRefreshInFlight: Boolean = false
    @Volatile private var lastAutoRefreshAtMs: Long = 0
    @Volatile private var lastStatusSignature: String? = null

    init {
        isOpaque = true
        background = UIManager.getColor("Panel.background") ?: Color.WHITE
        
        // 先设置布局
        layout = BorderLayout()

        setupCommitBox()
        setupLists()
        buildUI()
        refreshGitInfo()

        autoRefreshTimer = Timer(1200) { maybeAutoRefresh() }.apply {
            isRepeats = true
            initialDelay = 500
        }
    }

    override fun addNotify() {
        super.addNotify()
        autoRefreshTimer.start()
    }

    override fun removeNotify() {
        autoRefreshTimer.stop()
        super.removeNotify()
    }

    private fun maybeAutoRefresh() {
        // 仅在视图实际可见时刷新，避免后台无意义扫描
        if (!isShowing) return

        val now = System.currentTimeMillis()
        // 避免重复触发（即使 Timer 发生抖动）
        if (now - lastAutoRefreshAtMs < 800) return
        lastAutoRefreshAtMs = now

        if (autoRefreshInFlight) return

        val workspaceRoot = gui.getWorkspaceRoot() ?: return
        if (!File(workspaceRoot, ".git").exists()) return

        autoRefreshInFlight = true
        Thread {
            try {
                val gitStatus = getGitStatus(workspaceRoot)
                val signature =
                    buildString {
                        gitStatus.staged.forEach { append("S:").append(it.status).append(':').append(it.filePath).append('|') }
                        gitStatus.unstaged.forEach { append("U:").append(it.status).append(':').append(it.filePath).append('|') }
                    }
                SwingUtilities.invokeLater {
                    try {
                        // 视图可能在后台线程执行期间被隐藏/移除
                        if (!isShowing) return@invokeLater
                        if (signature != lastStatusSignature) {
                            lastStatusSignature = signature
                            updateChangesLists(gitStatus)
                        }
                    } finally {
                        autoRefreshInFlight = false
                    }
                }
            } catch (_: Exception) {
                autoRefreshInFlight = false
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun buildUI() {
        // CHANGES 部分（包含提交区域和文件列表）
        val changesContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = UIManager.getColor("Panel.background") ?: Color.WHITE
            alignmentX = Component.LEFT_ALIGNMENT
        }
        changesContent.add(createCommitPanel())
        changesContent.add(Box.createVerticalStrut(8))
        
        // Staged Changes 在上，Changes 在下
        changesContent.add(stagedSection.root)
        changesContent.add(Box.createVerticalStrut(6))
        changesContent.add(unstagedSection.root)
        
        changesSection = CollapsibleSection(
            title = "CHANGES",
            countLabel = null,
            content = changesContent,
            expanded = true,
            textColor = themeTextColor,
            mutedTextColor = themeMutedTextColor,
        )

        // GRAPH 部分
        val graphContent = createGraphPanel()
        graphSection = CollapsibleSection(
            title = "GRAPH",
            countLabel = null,
            content = graphContent,
            expanded = true,
            textColor = themeTextColor,
            mutedTextColor = themeMutedTextColor,
        )

        val scrollContent = VerticalScrollablePanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = UIManager.getColor("Panel.background") ?: Color.WHITE
            border = EmptyBorder(8, 10, 10, 10)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        scrollContent.add(changesSection.root)
        scrollContent.add(Box.createVerticalStrut(16))
        scrollContent.add(graphSection.root)

        val scroll = JScrollPane(scrollContent).apply {
            border = EmptyBorder(0, 0, 0, 0)
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            viewport.isOpaque = true
            viewport.background = UIManager.getColor("Panel.background") ?: Color.WHITE
            isOpaque = true
            background = UIManager.getColor("Panel.background") ?: Color.WHITE
        }

        removeAll()
        add(createTopHeader(), BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun createTopHeader(): JComponent {
        val title = JLabel("SOURCE CONTROL").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = themeMutedTextColor
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIManager.getColor("Panel.background") ?: Color.WHITE
            border = EmptyBorder(10, 10, 6, 10)
            add(title, BorderLayout.WEST)
        }
    }


    private fun createCommitPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = UIManager.getColor("Panel.background") ?: Color.WHITE
            alignmentX = Component.LEFT_ALIGNMENT
        }

        // 提交消息输入框（支持多行）
        val messageScrollPane = JScrollPane(commitMessageField).apply {
            border = commitMessageField.border
            // 避免使用 Int.MAX_VALUE 作为 preferredWidth（会导致部分布局计算异常）
            preferredSize = Dimension(1, 60)
            maximumSize = Dimension(Int.MAX_VALUE, 100)
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        commitMessageField.border = EmptyBorder(4, 8, 4, 8)
        commitMessageField.foreground = themeTextColor
        commitMessageField.caretColor = themeTextColor
        commitMessageField.placeholderColor = themeMutedTextColor
        panel.add(messageScrollPane)
        panel.add(Box.createVerticalStrut(8))

        // Commit 按钮（文本居中，无下拉框）
        commitButton.apply {
            maximumSize = Dimension(Int.MAX_VALUE, 32)
            horizontalAlignment = SwingConstants.CENTER
            addActionListener { performCommit() }
            foreground = themeTextColor
        }
        panel.add(commitButton)

        return panel
    }

    private fun createGraphPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = UIManager.getColor("Panel.background") ?: Color.WHITE
            alignmentX = Component.LEFT_ALIGNMENT
        }

        // 工具栏：左侧显示分支，右侧显示按钮（与标题对齐）
        val toolbar = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(0, 0, 6, 0)
        }

        val leftPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
        }
        
        graphBranchLabel = JLabel("Auto").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = themeMutedTextColor
        }
        leftPanel.add(graphBranchLabel!!)

        val rightPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
        }
        
        // 分支图标按钮
        val branchButton = createIconButton(IconRef("icons/common/git-branch.svg"), "分支")
        branchButton.addActionListener { 
            // TODO: 显示分支选择菜单
        }
        rightPanel.add(branchButton)
        
        // 眼睛图标按钮（查看）
        val eyeButton = createIconButton(IconRef("icons/common/search.svg"), "查看")
        eyeButton.addActionListener {
            // TODO: 实现查看功能
        }
        rightPanel.add(eyeButton)
        
        // 下拉箭头按钮
        val downArrowButton = createIconButton(IconRef("icons/common/arrow-down.svg"), "拉取")
        downArrowButton.addActionListener {
            // TODO: 实现拉取功能
        }
        rightPanel.add(downArrowButton)
        
        // 上箭头按钮
        val upArrowButton = createIconButton(IconRef("icons/common/arrow-up.svg"), "推送")
        upArrowButton.addActionListener {
            // TODO: 实现推送功能
        }
        rightPanel.add(upArrowButton)
        
        // 刷新按钮
        val refreshButton = createIconButton(IconRef("icons/common/refresh.svg"), "刷新")
        refreshButton.addActionListener { refreshGitInfo() }
        rightPanel.add(refreshButton)

        toolbar.add(leftPanel, BorderLayout.WEST)
        toolbar.add(Box.createHorizontalGlue(), BorderLayout.CENTER)
        toolbar.add(rightPanel, BorderLayout.EAST)

        panel.add(toolbar)
        panel.add(graphList)

        return panel
    }
    
    private fun createIconButton(iconRef: IconRef, tooltip: String): JButton {
        return JButton().apply {
            icon = IconLoader.getIcon(
                iconRef,
                14,
                adaptToTheme = true,
                getThemeColor = { themeTextColor },
                getDisabledColor = { themeMutedTextColor }
            )
            toolTipText = tooltip
            isFocusable = false
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            preferredSize = Dimension(20, 20)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    isOpaque = true
                    background = UIManager.getColor("Button.select") ?: Color(0x3F, 0x3F, 0x3F)
                }
                override fun mouseExited(e: MouseEvent) {
                    isOpaque = false
                }
            })
        }
    }

    private fun setupCommitBox() {
        val textFieldBorder = UIManager.getBorder("TextField.border")
            ?: BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") ?: Color.GRAY)
        commitMessageField.border = textFieldBorder
        commitMessageField.toolTipText = "输入提交信息"
        commitMessageField.lineWrap = true
        commitMessageField.wrapStyleWord = true
        commitMessageField.foreground = themeTextColor
        commitMessageField.caretColor = themeTextColor
        commitMessageField.placeholderColor = themeMutedTextColor

        commitMessageField.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateCommitButtonState()
                override fun removeUpdate(e: DocumentEvent?) = updateCommitButtonState()
                override fun changedUpdate(e: DocumentEvent?) = updateCommitButtonState()
            }
        )

        // 快捷键：Cmd/Ctrl + Enter 提交（Shift+Enter 换行）
        val shortcutMask = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        commitMessageField.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, shortcutMask),
            "git.commit",
        )
        commitMessageField.actionMap.put(
            "git.commit",
            object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    performCommit()
                }
            },
        )

        commitButton.isFocusable = false
        updateCommitButtonState()
    }

    private fun setupLists() {
        // Changes 列表
        setupFileList(unstagedChangesList)
        setupFileList(stagedChangesList)
        unstagedChangesList.cellRenderer = GitFileItemRenderer(themeTextColor, themeMutedTextColor)
        stagedChangesList.cellRenderer = GitFileItemRenderer(themeTextColor, themeMutedTextColor)

        // Graph 列表
        graphList.apply {
            val bg = UIManager.getColor("List.background")
                ?: UIManager.getColor("Panel.background")
                ?: Color(0, 0, 0)
            val fg = UIManager.getColor("List.foreground")
                ?: UIManager.getColor("Label.foreground")
                ?: Color.WHITE
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            fixedCellHeight = 24
            font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            isOpaque = false
            background = bg
            foreground = themeTextColor
            selectionBackground = UIManager.getColor("List.selectionBackground") ?: bg.darker()
            selectionForeground = UIManager.getColor("List.selectionForeground") ?: themeTextColor
            border = EmptyBorder(0, 0, 0, 0)
            cellRenderer = CommitItemRenderer(themeTextColor, themeMutedTextColor)
        }

        installOpenDiffHandlers(unstagedChangesList, DiffMode.UNSTAGED)
        installOpenDiffHandlers(stagedChangesList, DiffMode.STAGED)
    }

    private fun setupFileList(list: JList<GitFileItem>) {
        list.apply {
            val bg = UIManager.getColor("List.background")
                ?: UIManager.getColor("Panel.background")
                ?: Color(0, 0, 0)
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            fixedCellHeight = 26
            isOpaque = false
            background = bg
            foreground = themeTextColor
            selectionBackground = UIManager.getColor("List.selectionBackground") ?: bg.darker()
            selectionForeground = UIManager.getColor("List.selectionForeground") ?: themeTextColor
            border = EmptyBorder(0, 18, 0, 0)
        }
        updateListPreferredHeight(list)
    }

    private enum class DiffMode { STAGED, UNSTAGED }

    private fun installOpenDiffHandlers(list: JList<GitFileItem>, mode: DiffMode) {
        // 单击/键盘移动选中时实时打开 Diff（行为参考 VSCode）
        list.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            val item = list.selectedValue ?: return@addListSelectionListener
            if (item.file == null) return@addListSelectionListener
            openDiffForItem(item, mode)
        }

        // 处理“点击已选中项”时 selection 不变导致不触发的情况
        list.addMouseListener(
            object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (!SwingUtilities.isLeftMouseButton(e) || e.clickCount < 1) return
                    val idx = list.locationToIndex(e.point)
                    if (idx < 0) return
                    val bounds = list.getCellBounds(idx, idx) ?: return
                    if (!bounds.contains(e.point)) return
                    list.selectedIndex = idx
                    val item = list.model.getElementAt(idx)
                    if (item.file == null) return
                    openDiffForItem(item, mode)
                }
            }
        )

        // 回车打开 Diff
        list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "git.openDiff")
        list.actionMap.put(
            "git.openDiff",
            object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    val item = list.selectedValue ?: return
                    if (item.file == null) return
                    openDiffForItem(item, mode)
                }
            }
        )
    }

    private fun openDiffForItem(item: GitFileItem, mode: DiffMode) {
        val workspaceRoot = gui.getWorkspaceRoot() ?: return
        val rawPath = item.filePath
        val filePath = normalizeGitPath(rawPath)
        val fileName = File(filePath).name

        // 统一 Diff 语义：HEAD vs 当前版本（工作区）
        val key = "HEAD:$filePath"
        val now = System.currentTimeMillis()
        if (lastOpenedDiffKey == key && now - lastOpenedDiffAtMs < 200) {
            return
        }
        lastOpenedDiffKey = key
        lastOpenedDiffAtMs = now

        val file = File(workspaceRoot, filePath)
        val tabId = "git.diff:$filePath"
        val tabTitle = "Diff · $fileName"

        // 先立即打开一个占位 Diff，保证点击有反馈；后续后台计算完成会用同一 tabId 刷新内容
        runCatching {
            gui.openDiffTab(
                id = tabId,
                title = tabTitle,
                file = file,
                leftTitle = "HEAD",
                leftText = "加载中…",
                rightTitle = "当前",
                rightText = "加载中…",
                hunks = emptyList()
            )
        }.onFailure { e ->
            JOptionPane.showMessageDialog(
                this,
                "打开 Diff 失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        val requestId = ++diffRequestId
        gui.showProgress("打开 Diff：$fileName", indeterminate = true, cancellable = false)

        Thread {
            val result = runCatching {
                val headHash = getHeadShortHash(workspaceRoot)
                val headTitle = if (headHash.isNullOrBlank()) "HEAD" else "HEAD ($headHash)"
                val headText = gitShowOrEmpty(workspaceRoot, "HEAD:$filePath")
                val workingText = readWorkingTreeOrEmpty(file)
                val hunks = getGitDiffHunksAgainstHead(workspaceRoot, filePath)
                    .ifEmpty { fallbackHunksForUntracked(headText, workingText) }
                Quintuple(headTitle, "当前", headText, workingText, hunks)
            }.getOrElse {
                Quintuple("左侧", "右侧", "", "", emptyList())
            }

            SwingUtilities.invokeLater {
                if (requestId != diffRequestId) return@invokeLater
                gui.hideProgress()
                runCatching {
                    gui.openDiffTab(
                        id = tabId,
                        title = tabTitle,
                        file = file,
                        leftTitle = result.first,
                        leftText = result.third,
                        rightTitle = result.second,
                        rightText = result.fourth,
                        hunks = result.fifth
                    )
                }.onFailure { e ->
                    JOptionPane.showMessageDialog(
                        this,
                        "打开 Diff 失败: ${e.message}",
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

    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    private fun normalizeGitPath(filePath: String): String {
        // 处理 rename: "old -> new"（取 new）
        val arrowIdx = filePath.indexOf("->")
        if (arrowIdx >= 0) {
            return filePath.substring(arrowIdx + 2).trim()
        }
        return filePath.trim()
    }

    private fun gitShowOrEmpty(workspaceRoot: File, spec: String): String {
        return runCatching {
            val process = ProcessBuilder("git", "show", spec)
                .directory(workspaceRoot)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.reader(Charsets.UTF_8).use { it.readText() }
            process.waitFor()
            if (process.exitValue() == 0) output else ""
        }.getOrDefault("")
    }

    private fun readWorkingTreeOrEmpty(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        return runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
    }

    private fun getHeadShortHash(workspaceRoot: File): String? {
        return runCatching {
            val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(workspaceRoot)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.reader(Charsets.UTF_8).use { it.readText() }
            process.waitFor()
            if (process.exitValue() == 0) out.trim() else null
        }.getOrNull()
    }

    private fun getGitDiffHunksAgainstHead(workspaceRoot: File, filePath: String): List<DiffHunk> {
        // 使用 git diff 的 hunk 头行号来做高亮，避免自己实现复杂 diff 算法
        val args = mutableListOf("git", "diff", "--unified=0", "--no-color", "HEAD", "--", filePath)
        val output = runCatching {
            val process = ProcessBuilder(args)
                .directory(workspaceRoot)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.reader(Charsets.UTF_8).use { it.readText() }
            process.waitFor()
            if (process.exitValue() == 0) text else ""
        }.getOrDefault("")

        val regex = Regex("""@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
        return output.lineSequence()
            .mapNotNull { line ->
                val m = regex.find(line) ?: return@mapNotNull null
                val leftStart = m.groupValues[1].toInt()
                val leftCount = m.groupValues[2].ifBlank { "1" }.toInt()
                val rightStart = m.groupValues[3].toInt()
                val rightCount = m.groupValues[4].ifBlank { "1" }.toInt()
                DiffHunk(leftStart, leftCount, rightStart, rightCount)
            }
            .toList()
    }

    private fun fallbackHunksForUntracked(leftText: String, rightText: String): List<DiffHunk> {
        // 处理新文件/无法通过 git diff 解析到 hunk 的情况：直接高亮右侧全部内容
        if (leftText.isNotEmpty()) return emptyList()
        val lines = rightText.split('\n').size.coerceAtLeast(1)
        return listOf(DiffHunk(leftStart = 1, leftCount = 0, rightStart = 1, rightCount = lines))
    }

    private fun refreshGitInfo() {
        val workspaceRoot = gui.getWorkspaceRoot() ?: run {
            updateUIForNoWorkspace()
            return
        }

        val gitDir = File(workspaceRoot, ".git")
        if (!gitDir.exists()) {
            updateUIForNonGitRepository()
            return
        }

        Thread {
            try {
                val gitStatus = getGitStatus(workspaceRoot)
                val graphLines = getGitGraphLines(workspaceRoot)
                val branch = getCurrentBranch(workspaceRoot)
                SwingUtilities.invokeLater {
                    currentBranch = branch
                    updateCommitPlaceholder(branch)
                    updateChangesLists(gitStatus)
                    updateGitGraph(graphLines, branch)
                    updateGraphBranchLabel(branch)
                    lastStatusSignature =
                        buildString {
                            gitStatus.staged.forEach { append("S:").append(it.status).append(':').append(it.filePath).append('|') }
                            gitStatus.unstaged.forEach { append("U:").append(it.status).append(':').append(it.filePath).append('|') }
                        }
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

    private fun updateUIForNoWorkspace() {
        unstagedChangesModel.clear()
        stagedChangesModel.clear()
        graphModel.clear()
        unstagedChangesModel.addElement(GitFileItem("未打开工作区", "", null))
        stagedChangesModel.addElement(GitFileItem("未打开工作区", "", null))
        graphModel.addElement(CommitItem("未打开工作区", null, null, false))
        updateBadgesAndSizes()
    }

    private fun updateUIForNonGitRepository() {
        unstagedChangesModel.clear()
        stagedChangesModel.clear()
        graphModel.clear()
        unstagedChangesModel.addElement(GitFileItem("当前目录不是 Git 仓库", "", null))
        stagedChangesModel.addElement(GitFileItem("当前目录不是 Git 仓库", "", null))
        graphModel.addElement(CommitItem("当前目录不是 Git 仓库", null, null, false))
        updateBadgesAndSizes()
    }

    private fun updateCommitButtonState() {
        commitButton.isEnabled = commitMessageField.text.trim().isNotBlank()
    }

    private fun updateBadgesAndSizes() {
        updateCountBadge(unstagedChangesModel, unstagedCountLabel)
        updateCountBadge(stagedChangesModel, stagedCountLabel)
        updateListPreferredHeight(unstagedChangesList)
        updateListPreferredHeight(stagedChangesList)
        updateListPreferredHeight(graphList)
        revalidate()
        repaint()
    }

    private fun updateCommitPlaceholder(branch: String?) {
        val hint = shortcutHint()
        val target = branch?.takeIf { it.isNotBlank() } ?: "HEAD"
        commitMessageField.placeholder = "Message ($hint to commit on \"$target\")"
        commitMessageField.repaint()
    }
    
    private fun updateGraphBranchLabel(branch: String?) {
        graphBranchLabel?.text = branch?.takeIf { it.isNotBlank() } ?: "Auto"
    }

    private fun shortcutHint(): String {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("mac")) "⌘⏎" else "Ctrl+Enter"
    }

    private fun updateCountBadge(model: DefaultListModel<GitFileItem>, label: JLabel) {
        val count = (0 until model.size).count { idx -> model.getElementAt(idx).file != null }
        label.text = if (count > 0) count.toString() else ""
        label.isVisible = count > 0
    }

    private fun updateListPreferredHeight(list: JList<*>) {
        val rows = list.model.size.coerceAtLeast(1)
        val rowHeight = list.fixedCellHeight.takeIf { it > 0 } ?: 24
        val height = rows * rowHeight
        list.minimumSize = Dimension(1, height)
        list.preferredSize = Dimension(1, height)
        list.maximumSize = Dimension(Int.MAX_VALUE, height)
    }

    private fun updateChangesLists(gitStatus: GitStatus) {
        stagedChangesModel.clear()
        unstagedChangesModel.clear()

        if (gitStatus.staged.isEmpty() && gitStatus.unstaged.isEmpty()) {
            unstagedChangesModel.addElement(GitFileItem("无变更文件", "", null))
        } else {
            // Staged Changes 在上
            gitStatus.staged.forEach { stagedChangesModel.addElement(it) }
            // Changes 在下
            gitStatus.unstaged.forEach { unstagedChangesModel.addElement(it) }
        }

        // Staged Changes 为空时不显示
        stagedSection.root.isVisible = gitStatus.staged.isNotEmpty()
        
        updateBadgesAndSizes()
    }

    private fun updateGitGraph(lines: List<String>, branch: String?) {
        graphModel.clear()
        if (lines.isEmpty()) {
            graphModel.addElement(CommitItem("暂无提交历史", null, null, false))
        } else {
            lines.forEachIndexed { index, line ->
                val isCurrentBranch = index == 0 && branch != null
                val commitItem = parseCommitLine(line, branch, isCurrentBranch)
                graphModel.addElement(commitItem)
            }
        }
        updateBadgesAndSizes()
    }
    
    private fun parseCommitLine(line: String, currentBranch: String?, isCurrentBranch: Boolean): CommitItem {
        // 解析 git log --graph --oneline 的输出
        // 格式示例: "* a1b2c3d feat: 添加新功能 (HEAD -> dev, origin/dev)"
        val trimmed = line.trim()
        val graphChars = trimmed.takeWhile { it == '*' || it == '|' || it == '/' || it == '\\' || it == ' ' }
        val content = trimmed.substring(graphChars.length).trim()
        
        // 提取提交哈希和消息
        val parts = content.split(" ", limit = 2)
        val hash = if (parts.isNotEmpty()) parts[0].take(7) else ""
        val message = if (parts.size > 1) parts[1] else content
        
        // 提取分支信息
        val branchInfo = if (message.contains("(") && message.contains(")")) {
            val start = message.indexOf('(')
            val end = message.indexOf(')', start)
            if (start >= 0 && end > start) {
                message.substring(start + 1, end)
            } else null
        } else null
        
        val displayMessage = if (branchInfo != null) {
            message.substring(0, message.indexOf('(')).trim()
        } else {
            message
        }
        
        return CommitItem(displayMessage, hash, branchInfo, isCurrentBranch)
    }

    private fun getCurrentBranch(workspaceRoot: File): String? {
        return runCatching {
            val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(workspaceRoot)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            if (process.exitValue() == 0) output.trim() else null
        }.getOrNull()
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

                if (unstagedStatus != ' ') {
                    unstaged.add(GitFileItem(filePath, getStatusText(unstagedStatus), file))
                }
                if (stagedStatus != ' ') {
                    staged.add(GitFileItem(filePath, getStatusText(stagedStatus), file))
                }
            }
        }

        return GitStatus(staged = staged, unstaged = unstaged)
    }

    private fun getStatusText(status: Char): String {
        return when (status) {
            'M' -> "M"
            'A' -> "A"
            'D' -> "D"
            'R' -> "R"
            'C' -> "C"
            'U' -> "U"
            '?' -> "U" // Untracked
            '!' -> "!"
            else -> status.toString()
        }
    }

    private fun getGitGraphLines(workspaceRoot: File, limit: Int = 50): List<String> {
        return runCatching {
            val process = ProcessBuilder(
                "git",
                "log",
                "--graph",
                "--decorate",
                "--oneline",
                "--all",
                "-n",
                limit.toString(),
            )
                .directory(workspaceRoot)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            if (process.exitValue() != 0) return@runCatching emptyList<String>()
            output.lines().filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun performCommit() {
        val workspaceRoot = gui.getWorkspaceRoot() ?: return

        val gitDir = File(workspaceRoot, ".git")
        if (!gitDir.exists()) {
            JOptionPane.showMessageDialog(this, "当前目录不是 Git 仓库", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val message = commitMessageField.text.trim()
        if (message.isBlank()) {
            JOptionPane.showMessageDialog(this, "请输入提交信息", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val hasStaged =
            stagedChangesModel.size > 0 && (0 until stagedChangesModel.size).any { stagedChangesModel.getElementAt(it).file != null }
        if (!hasStaged) {
            JOptionPane.showMessageDialog(this, "没有需要提交的变更（请先暂存）", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        Thread {
            try {
                val commitProcess = ProcessBuilder("git", "commit", "-m", message)
                    .directory(workspaceRoot)
                    .redirectErrorStream(true)
                    .start()
                val output = commitProcess.inputStream.bufferedReader().use { it.readText() }
                val exitCode = commitProcess.waitFor()

                SwingUtilities.invokeLater {
                    if (exitCode == 0) {
                        commitMessageField.text = ""
                        updateCommitButtonState()
                        refreshGitInfo()
                        JOptionPane.showMessageDialog(this, "提交成功", "成功", JOptionPane.INFORMATION_MESSAGE)
                    } else {
                        JOptionPane.showMessageDialog(this, "提交失败: $output", "错误", JOptionPane.ERROR_MESSAGE)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(this, "提交时出错: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
    
    private fun performCommitAndPush() {
        val workspaceRoot = gui.getWorkspaceRoot() ?: return

        val gitDir = File(workspaceRoot, ".git")
        if (!gitDir.exists()) {
            JOptionPane.showMessageDialog(this, "当前目录不是 Git 仓库", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val message = commitMessageField.text.trim()
        if (message.isBlank()) {
            JOptionPane.showMessageDialog(this, "请输入提交信息", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val hasStaged =
            stagedChangesModel.size > 0 && (0 until stagedChangesModel.size).any { stagedChangesModel.getElementAt(it).file != null }
        if (!hasStaged) {
            JOptionPane.showMessageDialog(this, "没有需要提交的变更（请先暂存）", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        Thread {
            try {
                // 先提交
                val commitProcess = ProcessBuilder("git", "commit", "-m", message)
                    .directory(workspaceRoot)
                    .redirectErrorStream(true)
                    .start()
                val commitOutput = commitProcess.inputStream.bufferedReader().use { it.readText() }
                val commitExitCode = commitProcess.waitFor()

                if (commitExitCode != 0) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(this, "提交失败: $commitOutput", "错误", JOptionPane.ERROR_MESSAGE)
                    }
                    return@Thread
                }

                // 再推送
                val branch = currentBranch ?: "HEAD"
                val pushProcess = ProcessBuilder("git", "push", "origin", branch)
                    .directory(workspaceRoot)
                    .redirectErrorStream(true)
                    .start()
                val pushOutput = pushProcess.inputStream.bufferedReader().use { it.readText() }
                val pushExitCode = pushProcess.waitFor()

                SwingUtilities.invokeLater {
                    if (pushExitCode == 0) {
                        commitMessageField.text = ""
                        updateCommitButtonState()
                        refreshGitInfo()
                        JOptionPane.showMessageDialog(this, "提交并推送成功", "成功", JOptionPane.INFORMATION_MESSAGE)
                    } else {
                        JOptionPane.showMessageDialog(this, "推送失败: $pushOutput", "错误", JOptionPane.ERROR_MESSAGE)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(this, "操作时出错: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
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

    private class GitFileItemRenderer(
        private val textColor: Color,
        private val mutedTextColor: Color,
    ) : ListCellRenderer<GitFileItem> {
        override fun getListCellRendererComponent(
            list: JList<out GitFileItem>,
            value: GitFileItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val bg = if (isSelected) list.selectionBackground else list.background
            val fg = if (isSelected) list.selectionForeground else list.foreground

            val panel = JPanel(BorderLayout(8, 0)).apply {
                isOpaque = true
                background = bg
                border = EmptyBorder(2, 0, 2, 8)
            }

            if (value == null) return panel

            if (value.file == null) {
                panel.add(JLabel(value.filePath).apply { foreground = mutedTextColor }, BorderLayout.CENTER)
                return panel
            }

            val (name, dir) = splitFilePath(value.filePath)
            val nameLabel = JLabel(name).apply {
                foreground = if (isSelected) fg else textColor
                font = font.deriveFont(Font.PLAIN, 12f)
            }
            val dirLabel = JLabel(dir).apply {
                foreground = mutedTextColor
                font = font.deriveFont(Font.PLAIN, 11f)
            }
            val center = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                isOpaque = false
                add(nameLabel)
                if (dir.isNotBlank()) {
                    add(Box.createHorizontalStrut(6))
                    add(dirLabel)
                }
            }

            val statusLabel = JLabel(value.status).apply {
                horizontalAlignment = SwingConstants.RIGHT
                foreground = if (isSelected) fg else textColor
                font = font.deriveFont(Font.BOLD, 12f)
            }

            panel.add(center, BorderLayout.CENTER)
            panel.add(statusLabel, BorderLayout.EAST)
            return panel
        }

        private fun splitFilePath(path: String): Pair<String, String> {
            val normalized = path.replace('\\', '/')
            val idx = normalized.lastIndexOf('/')
            if (idx <= 0 || idx >= normalized.length - 1) return normalized to ""
            return normalized.substring(idx + 1) to normalized.substring(0, idx)
        }
    }

    private class CommitItemRenderer(
        private val textColor: Color,
        private val mutedTextColor: Color,
    ) : ListCellRenderer<CommitItem> {
        override fun getListCellRendererComponent(
            list: JList<out CommitItem>,
            value: CommitItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val bg = if (isSelected) list.selectionBackground else list.background
            val fg = if (isSelected) list.selectionForeground else list.foreground
            
            val panel = JPanel(BorderLayout(8, 0)).apply {
                isOpaque = true
                background = bg
                border = EmptyBorder(2, 12, 2, 8)
            }
            
            if (value == null) return panel
            
            // 左侧：提交点（蓝色圆点）
            val dotPanel = JPanel().apply {
                isOpaque = false
                preferredSize = Dimension(12, 12)
            }
            
            if (value.hash != null) {
                dotPanel.add(object : JComponent() {
                    override fun paintComponent(g: Graphics) {
                        val g2 = g.create() as Graphics2D
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2.color = Color(0x0E, 0x63, 0x9C) // 蓝色
                        val size = 8.0
                        val x = (width - size) / 2.0
                        val y = (height - size) / 2.0
                        g2.fill(Ellipse2D.Double(x, y, size, size))
                        g2.dispose()
                    }
                })
            }
            
            // 中间：提交信息
            val centerPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
            }
            
            val messageLabel = JLabel(value.message).apply {
                foreground = if (isSelected) fg else textColor
                font = font.deriveFont(Font.PLAIN, 11f)
            }
            centerPanel.add(messageLabel)
            
            // 如果有分支信息，显示在下方
            if (value.branchInfo != null) {
                val branchPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    isOpaque = false
                }
                
                // 眼睛图标（表示当前分支）
                if (value.isCurrentBranch) {
                    val eyeIcon = IconLoader.getIcon(IconRef("icons/common/search.svg"), 12)
                    val eyeLabel = JLabel(eyeIcon).apply {
                        toolTipText = "当前分支"
                    }
                    branchPanel.add(eyeLabel)
                }
                
                // 分支标签
                val branchLabel = JLabel(value.branchInfo).apply {
                    foreground = mutedTextColor
                    font = font.deriveFont(Font.PLAIN, 10f)
                }
                branchPanel.add(branchLabel)
                
                centerPanel.add(branchPanel)
            }
            
            panel.add(dotPanel, BorderLayout.WEST)
            panel.add(centerPanel, BorderLayout.CENTER)
            
            return panel
        }
    }
    
    data class CommitItem(
        val message: String,
        val hash: String?,
        val branchInfo: String?,
        val isCurrentBranch: Boolean
    )

    private class PlaceholderTextArea : JTextArea() {
        var placeholder: String = ""
        var placeholderColor: Color? = null

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (text.isNotEmpty() || isFocusOwner || placeholder.isBlank()) return

            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2.color = placeholderColor ?: UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
                val fm = g2.fontMetrics
                val insets = insets
                val x = insets.left + 2
                val y = insets.top + fm.ascent
                g2.drawString(placeholder, x, y)
            } finally {
                g2.dispose()
            }
        }
    }

    private class CollapsibleSection(
        val title: String,
        val countLabel: JLabel?,
        val content: JComponent,
        var expanded: Boolean,
        val textColor: Color,
        val mutedTextColor: Color,
    ) {
        val root: JPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = UIManager.getColor("Panel.background") ?: Color.WHITE
            alignmentX = Component.LEFT_ALIGNMENT
            minimumSize = Dimension(100, 50)
            preferredSize = Dimension(1, 50)
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }

        private val arrowLabel = JLabel()
        private val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = true
            background = UIManager.getColor("Panel.background") ?: Color.WHITE
            border = EmptyBorder(4, 0, 4, 0)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        init {
            val down = IconLoader.getIcon(IconRef("icons/common/chevron-down.svg"), 12)
            val right = IconLoader.getIcon(IconRef("icons/common/chevron-right.svg"), 12)
            arrowLabel.icon = if (expanded) down else right

            val titleLabel = JLabel(title).apply {
                font = font.deriveFont(Font.PLAIN, 12f)
                foreground = textColor
            }

            header.add(arrowLabel)
            header.add(Box.createHorizontalStrut(6))
            header.add(titleLabel)
            if (countLabel != null) {
                header.add(Box.createHorizontalGlue())
                header.add(countLabel)
            }

            val toggle = object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    expanded = !expanded
                    arrowLabel.icon = if (expanded) down else right
                    content.isVisible = expanded
                    root.revalidate()
                    root.repaint()
                }
            }
            header.addMouseListener(toggle)
            arrowLabel.addMouseListener(toggle)
            titleLabel.addMouseListener(toggle)
            countLabel?.addMouseListener(toggle)

            root.add(header)
            content.isVisible = expanded
            // 确保 content 没有父容器，避免重复添加
            val parent = content.parent
            if (parent != null) {
                parent.remove(content)
            }
            root.add(content)
            // 确保 root 有最小大小和可见性
            root.minimumSize = Dimension(100, 50)
            root.isVisible = true
            root.isOpaque = true
        }
    }

    private fun createCountBadge(): JLabel {
        val bg = Color(0x0E, 0x63, 0x9C)
        return JLabel("").apply {
            isOpaque = true
            background = bg
            foreground = Color.WHITE
            font = font.deriveFont(Font.BOLD, 11f)
            border = EmptyBorder(1, 6, 1, 6)
            isVisible = false
        }
    }

    /**
     * 让 ScrollPane 的 viewport 始终按宽度撑满，避免内容较窄时被 ViewportLayout 居中对齐。
     */
    private class VerticalScrollablePanel : JPanel(), Scrollable {
        override fun getPreferredSize(): Dimension {
            val base = super.getPreferredSize()
            val viewportHeight = (parent as? javax.swing.JViewport)?.height ?: 0
            // 视图高度至少与 viewport 一致，避免内容高度较小时被压缩在一起
            return Dimension(base.width, maxOf(base.height, viewportHeight))
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = false

        override fun getScrollableUnitIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int
        ): Int = 16

        override fun getScrollableBlockIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int
        ): Int = visibleRect.height.coerceAtLeast(64)
    }
}
