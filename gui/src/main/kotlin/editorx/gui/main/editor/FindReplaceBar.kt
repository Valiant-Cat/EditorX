package editorx.gui.main.editor

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.util.IconLoader
import editorx.core.util.IconRef
import editorx.gui.theme.ThemeManager
import org.fife.ui.rtextarea.SearchContext
import org.fife.ui.rtextarea.SearchEngine
import org.fife.ui.rtextarea.SearchResult
import org.fife.ui.rsyntaxtextarea.DocumentRange
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 文件内查找/替换条（参考 IDEA：在编辑器内展示，不弹窗）。
 */
class FindReplaceBar(
    private val currentTextAreaProvider: () -> TextArea?,
) : JPanel(BorderLayout()) {
    companion object {
        private const val MIN_FIELD_WIDTH = 220
        private const val MAX_FIELD_WIDTH = 600
        private const val FIELD_PADDING = 36
    }

    enum class Mode { FIND, REPLACE }

    private var mode: Mode = Mode.FIND
    private var lastTextArea: TextArea? = null
    private var lastCriteriaKey: String = ""
    private var totalMatches: Int = 0
    private var currentMatchIndex: Int = 0

    private data class Match(val start: Int, val end: Int)

    private val findField = JTextField()
    private val replaceField = JTextField()
    private val statusLabel = JLabel().apply {
        foreground = Color(0x66, 0x66, 0x66)
        font = font.deriveFont(Font.PLAIN, 12f)
        text = I18n.translate(I18nKeys.FindReplace.NO_RESULTS)
    }

    private val expandIconCollapsed = IconLoader.getIcon(IconRef("icons/common/chevron-right.svg"), 16)
    private val expandIconExpanded = IconLoader.getIcon(IconRef("icons/common/chevron-down.svg"), 16)
    private val prevIcon = IconLoader.getIcon(IconRef("icons/common/arrow-up.svg"), 16)
    private val nextIcon = IconLoader.getIcon(IconRef("icons/common/arrow-down.svg"), 16)
    private val closeIcon = IconLoader.getIcon(IconRef("icons/common/close.svg"), 24)
    private val expandButton = createIconButton(expandIconCollapsed, I18n.translate(I18nKeys.FindReplace.EXPAND_REPLACE), fallbackText = "▸") {
        toggleReplaceRow()
    }
    private val matchCase = createToggleButton("Cc", I18n.translate(I18nKeys.FindReplace.MATCH_CASE))
    private val wholeWord = createToggleButton("W", I18n.translate(I18nKeys.FindReplace.WHOLE_WORD))
    private val regex = createToggleButton(".*", I18n.translate(I18nKeys.FindReplace.REGEX))

    private val replaceButton = createGhostButton(I18n.translate(I18nKeys.FindReplace.REPLACE_ONE), I18n.translate(I18nKeys.FindReplace.REPLACE_ONE_TOOLTIP)) { replaceOne() }
    private val replaceAllButton = createGhostButton(I18n.translate(I18nKeys.FindReplace.REPLACE_ALL), I18n.translate(I18nKeys.FindReplace.REPLACE_ALL_TOOLTIP)) { replaceAll() }

    private val findPrevButton = createIconButton(prevIcon, I18n.translate(I18nKeys.FindReplace.FIND_PREV), fallbackText = "↑") { findNext(forward = false) }
    private val findNextButton = createIconButton(nextIcon, I18n.translate(I18nKeys.FindReplace.FIND_NEXT), fallbackText = "↓") { findNext(forward = true) }
    private val closeButton = createIconButton(closeIcon, I18n.translate(I18nKeys.FindReplace.CLOSE), fallbackText = "×") { close() }

    private val replaceIndent = JPanel().apply {
        isOpaque = false
    }
    private val searchControls = JPanel(BorderLayout()).apply {
        isOpaque = false
    }
    private val searchLeftControls = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
    }
    private val replaceControls = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = LEFT_ALIGNMENT
    }

    init {
        isVisible = false
        background = ThemeManager.activityBarBackground
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.separator),
            BorderFactory.createEmptyBorder(6, 8, 6, 8),
        )

        // FlatLaf：占位提示
        findField.putClientProperty("JTextField.placeholderText", I18n.translate(I18nKeys.FindReplace.SEARCH))
        replaceField.putClientProperty("JTextField.placeholderText", I18n.translate(I18nKeys.FindReplace.REPLACE))

        // 基础输入尺寸（更贴近 IDEA 的紧凑感）
        val fieldHeight = 28
        val minFieldWidth = MIN_FIELD_WIDTH
        val maxFieldWidth = MAX_FIELD_WIDTH
        val minDimension = Dimension(minFieldWidth, fieldHeight)
        findField.minimumSize = minDimension
        findField.preferredSize = minDimension
        findField.maximumSize = Dimension(maxFieldWidth, fieldHeight)
        replaceField.minimumSize = minDimension
        replaceField.preferredSize = minDimension
        replaceField.maximumSize = Dimension(maxFieldWidth, fieldHeight)

        // 右侧控制区（放到单独列，确保“搜索/替换”输入框左右对齐）
        searchLeftControls.add(statusLabel)
        searchLeftControls.add(Box.createHorizontalStrut(12))
        searchLeftControls.add(matchCase)
        searchLeftControls.add(Box.createHorizontalStrut(4))
        searchLeftControls.add(wholeWord)
        searchLeftControls.add(Box.createHorizontalStrut(4))
        searchLeftControls.add(regex)
        searchLeftControls.add(Box.createHorizontalStrut(8))
        searchLeftControls.add(findPrevButton)
        searchLeftControls.add(Box.createHorizontalStrut(2))
        searchLeftControls.add(findNextButton)
        searchControls.add(searchLeftControls, BorderLayout.WEST)
        searchControls.add(closeButton, BorderLayout.EAST)

        replaceControls.add(replaceButton)
        replaceControls.add(Box.createHorizontalStrut(6))
        replaceControls.add(replaceAllButton)

        val grid = JPanel(GridBagLayout()).apply { isOpaque = false }
        val gc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.NONE
            weightx = 0.0
        }
        fun addCell(
            x: Int,
            y: Int,
            comp: java.awt.Component,
            weightX: Double = 0.0,
            fill: Int = GridBagConstraints.NONE,
            anchor: Int = GridBagConstraints.WEST,
            insets: Insets,
        ) {
            gc.gridx = x
            gc.gridy = y
            gc.weightx = weightX
            gc.fill = fill
            gc.anchor = anchor
            gc.insets = insets
            grid.add(comp, gc)
        }

        // 行 1：搜索
        addCell(0, 0, expandButton, insets = Insets(0, 0, 0, 6))
        addCell(1, 0, findField, weightX = 0.0, fill = GridBagConstraints.NONE, insets = Insets(0, 0, 0, 8))
        addCell(2, 0, searchLeftControls, weightX = 0.0, anchor = GridBagConstraints.WEST, insets = Insets(0, 0, 0, 0))

        // 行 2：替换（默认隐藏）
        addCell(0, 1, replaceIndent, insets = Insets(6, 0, 0, 6))
        addCell(1, 1, replaceField, weightX = 0.0, fill = GridBagConstraints.NONE, insets = Insets(6, 0, 0, 8))
        addCell(2, 1, replaceControls, anchor = GridBagConstraints.WEST, insets = Insets(6, 0, 0, 0))

        val topRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(grid, BorderLayout.WEST)
            val buttonHolder = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(Box.createVerticalStrut(2))
                add(closeButton)
                add(Box.createVerticalGlue())
            }
            add(buttonHolder, BorderLayout.EAST)
        }
        add(topRow, BorderLayout.CENTER)

        // 监听输入变化：实时高亮所有匹配
        findField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = refreshMarkAll()
            override fun removeUpdate(e: DocumentEvent?) = refreshMarkAll()
            override fun changedUpdate(e: DocumentEvent?) = refreshMarkAll()
        })
        findField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = adjustFieldWidth(findField)
            override fun removeUpdate(e: DocumentEvent?) = adjustFieldWidth(findField)
            override fun changedUpdate(e: DocumentEvent?) = adjustFieldWidth(findField)
        })
        replaceField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = adjustFieldWidth(replaceField)
            override fun removeUpdate(e: DocumentEvent?) = adjustFieldWidth(replaceField)
            override fun changedUpdate(e: DocumentEvent?) = adjustFieldWidth(replaceField)
        })
        // 初始化宽度
        adjustFieldWidth(findField)
        adjustFieldWidth(replaceField)

        // 监听选项变化
        val optionListener = java.awt.event.ActionListener { refreshMarkAll() }
        matchCase.addActionListener(optionListener)
        regex.addActionListener(optionListener)
        wholeWord.addActionListener(optionListener)

        installKeyBindings()
        setMode(Mode.FIND)

        // 计算替换行的对齐缩进：与展开按钮对齐
        SwingUtilities.invokeLater {
            val w = expandButton.preferredSize.width
            replaceIndent.preferredSize = Dimension(w, 1)
            replaceIndent.minimumSize = Dimension(w, 1)
        }
    }

    fun open(mode: Mode, initialQuery: String? = null) {
        setMode(mode)
        isVisible = true

        if (!initialQuery.isNullOrBlank()) {
            findField.text = initialQuery
        }

        // 聚焦查找框，便于直接输入
        SwingUtilities.invokeLater {
            findField.requestFocusInWindow()
            findField.selectAll()
            refreshMarkAll()
        }
    }

    fun close() {
        isVisible = false
        lastCriteriaKey = ""
        totalMatches = 0
        currentMatchIndex = 0
        statusLabel.text = I18n.translate(I18nKeys.FindReplace.NO_RESULTS)
        clearHighlights()
        currentTextAreaProvider()?.requestFocusInWindow()
    }

    /**
     * 当编辑器切换标签页时调用：把高亮应用到当前文件，并清理旧文件的高亮。
     */
    fun onActiveEditorChanged() {
        if (!isVisible) return
        refreshMarkAll()
    }

    private fun installKeyBindings() {
        val nextKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        val prevKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
        val escKey = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)

        findField.getInputMap(JComponent.WHEN_FOCUSED).put(nextKey, "findNext")
        findField.actionMap.put("findNext", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = findNext(forward = true)
        })
        findField.getInputMap(JComponent.WHEN_FOCUSED).put(prevKey, "findPrev")
        findField.actionMap.put("findPrev", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = findNext(forward = false)
        })

        // 替换输入框 Enter：替换一次
        replaceField.getInputMap(JComponent.WHEN_FOCUSED).put(nextKey, "replaceOne")
        replaceField.actionMap.put("replaceOne", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = replaceOne()
        })

        // Esc：关闭
        listOf(findField, replaceField).forEach { field ->
            field.getInputMap(JComponent.WHEN_FOCUSED).put(escKey, "closeFindBar")
            field.actionMap.put("closeFindBar", object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) = close()
            })
        }
    }

    private fun setMode(mode: Mode) {
        this.mode = mode
        val replaceVisible = mode == Mode.REPLACE
        replaceIndent.isVisible = replaceVisible
        replaceField.isVisible = replaceVisible
        replaceControls.isVisible = replaceVisible
        expandButton.icon = if (replaceVisible) expandIconExpanded else expandIconCollapsed
        // 兼容：若 SVG 不可用，则回退使用字符
        if (expandButton.icon == null) {
            expandButton.text = if (replaceVisible) "▾" else "▸"
        } else {
            expandButton.text = null
        }
        expandButton.toolTipText = if (replaceVisible) I18n.translate(I18nKeys.FindReplace.COLLAPSE_REPLACE) else I18n.translate(I18nKeys.FindReplace.EXPAND_REPLACE)
        revalidate()
        repaint()
    }

    private fun toggleReplaceRow() {
        setMode(if (mode == Mode.REPLACE) Mode.FIND else Mode.REPLACE)
        SwingUtilities.invokeLater {
            if (mode == Mode.REPLACE) replaceField.requestFocusInWindow() else findField.requestFocusInWindow()
        }
    }

    private fun clearHighlights() {
        lastTextArea?.clearMarkAllHighlights()
        lastTextArea = null
    }

    private fun criteriaKey(): String {
        return buildString {
            append(findField.text ?: "")
            append('\u0001')
            append(matchCase.isSelected)
            append('\u0001')
            append(wholeWord.isSelected)
            append('\u0001')
            append(regex.isSelected)
        }
    }

    private fun updateStatusLabel() {
        statusLabel.text =
            if (totalMatches <= 0) {
                I18n.translate(I18nKeys.FindReplace.NO_RESULTS)
            } else {
                I18n.translate(I18nKeys.FindReplace.RESULTS).format(
                    currentMatchIndex.coerceIn(1, totalMatches),
                    totalMatches
                )
            }
    }

    private fun isRegexValidOrShowError(): Boolean {
        if (!regex.isSelected) return true
        val query = findField.text ?: ""
        if (query.isBlank()) return true

        val patternText = if (wholeWord.isSelected) "\\b${query}\\b" else query
        val flags = RSyntaxUtilities.getPatternFlags(matchCase.isSelected, Pattern.MULTILINE)
        return try {
            Pattern.compile(patternText, flags)
            true
        } catch (_: PatternSyntaxException) {
            totalMatches = 0
            currentMatchIndex = 0
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.INVALID_REGEX)
            clearHighlights()
            false
        }
    }

    private fun collectMatches(text: String): List<Match> {
        val query = findField.text ?: ""
        if (query.isBlank()) return emptyList()

        return if (regex.isSelected) {
            val patternText = if (wholeWord.isSelected) "\\b${query}\\b" else query
            val flags = RSyntaxUtilities.getPatternFlags(matchCase.isSelected, Pattern.MULTILINE)
            val pattern = Pattern.compile(patternText, flags)
            val matcher = pattern.matcher(text)
            buildList {
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    if (start != end) add(Match(start, end))
                }
            }
        } else {
            val wholeWordEnabled = wholeWord.isSelected
            var searchIn = text
            var searchFor = query
            var matchCaseEnabled = matchCase.isSelected
            if (!matchCaseEnabled) {
                searchIn = searchIn.lowercase()
                searchFor = searchFor.lowercase()
                matchCaseEnabled = true
            }

            val matches = ArrayList<Match>()
            var offset = 0
            val len = searchFor.length
            while (offset <= searchIn.length - len) {
                val pos = SearchEngine.getNextMatchPos(searchFor, searchIn.substring(offset), true, matchCaseEnabled, wholeWordEnabled)
                if (pos < 0) break
                val start = offset + pos
                val end = start + len
                matches.add(Match(start, end))
                offset = end
            }
            matches
        }
    }

    private fun indexFromRange(matches: List<Match>, range: DocumentRange?): Int {
        if (range == null || range.isZeroLength || matches.isEmpty()) return 0
        val start = range.startOffset
        val end = range.endOffset
        val idx = matches.indexOfFirst { it.start == start && it.end == end }
        if (idx >= 0) return idx + 1
        val after = matches.indexOfFirst { it.start > start }
        return when {
            after > 0 -> after
            after == 0 -> 1
            else -> matches.size
        }
    }

    private fun refreshMarkAll() {
        val textArea = currentTextAreaProvider()
        if (textArea != lastTextArea) {
            lastTextArea?.clearMarkAllHighlights()
            lastTextArea = textArea
        }

        val query = findField.text ?: ""
        if (textArea == null || query.isBlank()) {
            textArea?.clearMarkAllHighlights()
            lastCriteriaKey = ""
            totalMatches = 0
            currentMatchIndex = 0
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.NO_RESULTS)
            return
        }

        if (!isRegexValidOrShowError()) return

        val key = criteriaKey()
        lastCriteriaKey = key

        runCatching {
            val context = buildContext(searchForward = true).apply { setMarkAll(true) }
            SearchEngine.markAll(textArea, context)
        }.onFailure {
            // 兜底：理论上正则错误已提前处理，这里仅保证 UI 不崩溃
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.SEARCH_FAILED)
            return
        }

        val matches = runCatching { collectMatches(textArea.text) }.getOrElse {
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.INVALID_REGEX)
            clearHighlights()
            totalMatches = 0
            currentMatchIndex = 0
            return
        }
        totalMatches = matches.size

        if (totalMatches <= 0) {
            currentMatchIndex = 0
            updateStatusLabel()
            return
        }

        // 参考 IDEA：输入变化时自动定位到“下一个匹配”，并显示 index/count
        val findResult = runCatching {
            val ctx = buildContext(searchForward = true).apply {
                setMarkAll(false)
                setSearchWrap(true)
            }
            SearchEngine.find(textArea, ctx)
        }.getOrElse {
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.SEARCH_FAILED)
            return
        }

        currentMatchIndex =
            if (findResult.wasFound()) indexFromRange(matches, findResult.matchRange) else 0
        if (currentMatchIndex <= 0) currentMatchIndex = 1
        updateStatusLabel()
    }

    private fun findNext(forward: Boolean) {
        val textArea = currentTextAreaProvider()
        val query = findField.text ?: ""
        if (textArea == null) {
            totalMatches = 0
            currentMatchIndex = 0
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.NO_RESULTS)
            return
        }
        if (query.isBlank()) {
            return
        }

        if (!isRegexValidOrShowError()) return

        runCatching {
            val context = buildContext(searchForward = forward).apply {
                setMarkAll(true)
                setSearchWrap(true)
            }
            SearchEngine.find(textArea, context)
        }.onSuccess { result ->
            lastCriteriaKey = criteriaKey()

            val matches = runCatching { collectMatches(textArea.text) }.getOrElse {
                statusLabel.text = I18n.translate(I18nKeys.FindReplace.INVALID_REGEX)
                clearHighlights()
                totalMatches = 0
                currentMatchIndex = 0
                return@onSuccess
            }
            totalMatches = matches.size
            currentMatchIndex = if (result.wasFound()) indexFromRange(matches, result.matchRange) else 0
            updateStatusLabel()
        }.onFailure {
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.SEARCH_FAILED)
        }
    }

    private fun replaceOne() {
        val textArea = currentTextAreaProvider()
        val query = findField.text ?: ""
        if (textArea == null) {
            totalMatches = 0
            currentMatchIndex = 0
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.NO_RESULTS)
            return
        }
        if (query.isBlank()) {
            return
        }

        if (!isRegexValidOrShowError()) return

        runCatching {
            val context = buildContext(searchForward = true).apply {
                setReplaceWith(replaceField.text ?: "")
                setMarkAll(true)
                setSearchWrap(true)
            }
            SearchEngine.replace(textArea, context)
        }.onSuccess { result ->
            lastCriteriaKey = criteriaKey()

            val matches = runCatching { collectMatches(textArea.text) }.getOrElse {
                statusLabel.text = I18n.translate(I18nKeys.FindReplace.INVALID_REGEX)
                clearHighlights()
                totalMatches = 0
                currentMatchIndex = 0
                return@onSuccess
            }
            totalMatches = matches.size
            currentMatchIndex = if (result.wasFound()) indexFromRange(matches, result.matchRange) else 0
            updateStatusLabel()
        }.onFailure {
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.REPLACE_FAILED)
        }
    }

    private fun replaceAll() {
        val textArea = currentTextAreaProvider()
        val query = findField.text ?: ""
        if (textArea == null) {
            totalMatches = 0
            currentMatchIndex = 0
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.NO_RESULTS)
            return
        }
        if (query.isBlank()) {
            return
        }

        if (!isRegexValidOrShowError()) return

        runCatching {
            val context = buildContext(searchForward = true).apply {
                setReplaceWith(replaceField.text ?: "")
                setMarkAll(false)
            }
            SearchEngine.replaceAll(textArea, context)
        }.onSuccess { _ ->
            // replaceAll 后以 refreshMarkAll() 的结果为准，这里先重置
            totalMatches = 0
            currentMatchIndex = 0
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.NO_RESULTS)
        }.onFailure {
            statusLabel.text = I18n.translate(I18nKeys.FindReplace.REPLACE_FAILED)
            return
        }
        // 替换后刷新高亮（如果查找条仍在）
        refreshMarkAll()
    }

    private fun buildContext(searchForward: Boolean): SearchContext {
        return SearchContext().apply {
            setSearchFor(findField.text ?: "")
            setMatchCase(this@FindReplaceBar.matchCase.isSelected)
            setWholeWord(this@FindReplaceBar.wholeWord.isSelected)
            setRegularExpression(this@FindReplaceBar.regex.isSelected)
            setSearchForward(searchForward)
        }
    }

    private fun updateStatus(result: SearchResult, searching: Boolean) = Unit
    
    private fun adjustFieldWidth(field: JTextField) {
        val fm = field.getFontMetrics(field.font)
        val textSample = field.text.ifEmpty {
            field.getClientProperty("JTextField.placeholderText") as? String ?: ""
        }
        val desiredWidth = (fm.stringWidth(textSample) + FIELD_PADDING)
            .coerceIn(MIN_FIELD_WIDTH, MAX_FIELD_WIDTH)
        val height = field.preferredSize.height.takeIf { it > 0 } ?: field.minimumSize.height
        val newSize = Dimension(desiredWidth, height)
        field.preferredSize = newSize
        field.minimumSize = Dimension(MIN_FIELD_WIDTH, height)
        field.maximumSize = Dimension(MAX_FIELD_WIDTH, height)
        field.revalidate()
        field.parent?.revalidate()
        field.parent?.repaint()
    }

    private fun createToggleButton(text: String, tooltip: String): JToggleButton {
        return JToggleButton(text).apply {
            toolTipText = tooltip
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 12f)
            margin = Insets(2, 8, 2, 8)
            putClientProperty("JButton.buttonType", "toolBarButton")
            putClientProperty("JButton.squareSize", true)
        }
    }

    private fun createToolButton(text: String, tooltip: String, onClick: () -> Unit): JButton {
        return JButton(text).apply {
            toolTipText = tooltip
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 12f)
            margin = Insets(2, 8, 2, 8)
            putClientProperty("JButton.buttonType", "toolBarButton")
            addActionListener { onClick() }
        }
    }
    
    private fun createGhostButton(text: String, tooltip: String, onClick: () -> Unit): java.awt.Component {
        return object : JPanel() {
            private var isHovered = false
            
            init {
                toolTipText = tooltip
                isOpaque = false
                border = BorderFactory.createEmptyBorder(6, 20, 6, 20)  // padding
                preferredSize = Dimension(
                    getFontMetrics(font.deriveFont(Font.PLAIN, 12f)).stringWidth(text) + 40,
                    28
                )
                
                addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) {
                        onClick()
                    }
                    
                    override fun mouseEntered(e: java.awt.event.MouseEvent) {
                        isHovered = true
                        repaint()
                    }
                    
                    override fun mouseExited(e: java.awt.event.MouseEvent) {
                        isHovered = false
                        repaint()
                    }
                })
                
                cursor = java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)
            }
            
            override fun paintComponent(g: java.awt.Graphics) {
                val g2 = g.create() as java.awt.Graphics2D
                try {
                    g2.setRenderingHint(
                        java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                    )
                    // 绘制圆角边框
                    g2.color = Color(0xCC, 0xCC, 0xCC)
                    g2.drawRoundRect(0, 0, width - 1, height - 1, 4, 4)
                    
                    // 绘制文本
                    g2.font = font.deriveFont(Font.PLAIN, 12f)
                    g2.color = Color(0x33, 0x33, 0x33)
                    val fm = g2.fontMetrics
                    val textWidth = fm.stringWidth(text)
                    val textHeight = fm.height
                    val x = (width - textWidth) / 2
                    val y = (height - textHeight) / 2 + fm.ascent
                    g2.drawString(text, x, y)
                } finally {
                    g2.dispose()
                }
            }
        }
    }
    
    private fun createIconButton(icon: javax.swing.Icon?, tooltip: String, fallbackText: String, onClick: () -> Unit): JButton {
        return JButton(fallbackText).apply {
            this.icon = icon
            toolTipText = tooltip
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 12f)
            margin = Insets(2, 6, 2, 6)
            putClientProperty("JButton.buttonType", "toolBarButton")
            putClientProperty("JButton.squareSize", true)
            if (icon != null) {
                text = null
            }
            addActionListener { onClick() }
        }
    }
}
