package editorx.gui.workbench.agent

import editorx.gui.MainWindow
import editorx.gui.ai.AiConfigStore
import editorx.gui.ai.AiMessage
import editorx.gui.ai.AiRole
import editorx.gui.ai.AiModelConfig
import editorx.gui.ai.OpenAiResponsesClient
import editorx.gui.ai.AiChatResult
import editorx.gui.theme.ThemeManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class AgentPanel(private val mainWindow: MainWindow) : JPanel(BorderLayout()) {

    private data class MessageRow(
        val role: AiRole,
        val bubble: MessageBubble,
        val headerLabel: JLabel,
        val textArea: JTextArea,
        var text: String,
        var isError: Boolean = false
    )

    private data class ModelOption(
        val id: String,
        val label: String,
        val config: AiModelConfig?
    ) {
        override fun toString(): String = label
    }

    private val configStore = AiConfigStore(mainWindow.guiContext.getAppDir())
    private var modelOptions: List<ModelOption> = emptyList()
    private var activeModel: AiModelConfig? = null

    private val client = OpenAiResponsesClient()
    private val conversation = mutableListOf<AiMessage>()
    private val messageRows = mutableListOf<MessageRow>()

    private val headerTitle = JLabel("Agent")
    private val headerSubtitle = JLabel("上下文助手")
    private val modelLabel = JLabel("模型")

    private val modelSelector = JComboBox<ModelOption>()
    private val reloadButton = JButton("刷新")
    private val configButton = JButton("配置")
    private val newChatButton = JButton("新对话")
    private val emptyConfigButton = JButton("配置")
    private val emptyReloadButton = JButton("刷新")

    private val messageList = JPanel()
    private val messageScroll = JScrollPane(messageList)
    private val emptyState = createEmptyState()

    private val bodyCard = java.awt.CardLayout()
    private val bodyPanel = JPanel(bodyCard)

    private lateinit var headerPanel: JPanel
    private lateinit var inputContainer: JPanel
    private lateinit var inputWrapper: RoundedPanel

    private val inputArea = JTextArea(4, 20)
    private val sendButton = JButton("发送")
    private val statusLabel = JLabel(" ")

    @Volatile
    private var isSending = false

    init {
        isOpaque = true
        layout = BorderLayout()
        buildHeader()
        buildBody()
        buildInput()
        reloadModels()
        updateTheme()

        ThemeManager.addThemeChangeListener { updateTheme() }
    }

    private fun buildHeader() {
        val titleBlock = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(headerTitle)
            add(Box.createVerticalStrut(2))
            add(headerSubtitle)
        }

        val selectorBlock = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(modelLabel)
            add(Box.createHorizontalStrut(6))
            modelSelector.preferredSize = Dimension(180, 26)
            add(modelSelector)
            add(Box.createHorizontalStrut(8))
            add(reloadButton)
            add(Box.createHorizontalStrut(6))
            add(configButton)
        }

        val topRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(titleBlock, BorderLayout.WEST)
            add(selectorBlock, BorderLayout.EAST)
        }

        val actionRow = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(newChatButton)
            add(Box.createHorizontalGlue())
        }

        headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(14, 14, 10, 14)
            add(topRow, BorderLayout.NORTH)
            add(Box.createVerticalStrut(8), BorderLayout.CENTER)
            add(actionRow, BorderLayout.SOUTH)
        }

        add(headerPanel, BorderLayout.NORTH)

        reloadButton.addActionListener { reloadModels() }
        configButton.addActionListener { openConfigFile() }
        newChatButton.addActionListener { resetConversation() }

        modelSelector.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val option = value as? ModelOption
                comp.text = option?.label ?: ""
                comp.border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
                return comp
            }
        }

        modelSelector.addActionListener {
            val selected = modelSelector.selectedItem as? ModelOption ?: return@addActionListener
            activeModel = selected.config
            selected.config?.let { configStore.saveActive(it.id) }
            updateInputState()
        }
    }

    private fun buildBody() {
        messageList.layout = BoxLayout(messageList, BoxLayout.Y_AXIS)
        messageList.isOpaque = false

        messageScroll.apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            // 部分 LAF 的 JViewport 不支持 setBorder，避免抛异常
            viewport.isOpaque = false
            isOpaque = false
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        bodyPanel.isOpaque = false
        bodyPanel.add(emptyState, "empty")
        bodyPanel.add(messageScroll, "messages")
        updateEmptyState()

        add(bodyPanel, BorderLayout.CENTER)
    }

    private fun buildInput() {
        inputContainer = JPanel(BorderLayout()).apply {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(10, 14, 14, 14)
        }

        inputArea.apply {
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
            font = font.deriveFont(Font.PLAIN, 12f)
        }

        inputWrapper = RoundedPanel(12) { ThemeManager.currentTheme.surface }.apply {
            border = BorderFactory.createLineBorder(ThemeManager.currentTheme.outline, 1, true)
            layout = BorderLayout()
            add(inputArea, BorderLayout.CENTER)
        }

        val helperRow = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(statusLabel)
            add(Box.createHorizontalGlue())
            add(sendButton)
        }

        inputContainer.add(inputWrapper, BorderLayout.CENTER)
        inputContainer.add(Box.createVerticalStrut(8), BorderLayout.NORTH)
        inputContainer.add(helperRow, BorderLayout.SOUTH)

        add(inputContainer, BorderLayout.SOUTH)

        sendButton.addActionListener { submitMessage() }

        val submitAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                submitMessage()
            }
        }

        inputArea.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "submitMessage")
        inputArea.actionMap.put("submitMessage", submitAction)
        inputArea.inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), "insert-newline")
        inputArea.actionMap.put("insert-newline", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                inputArea.append("\n")
            }
        })
    }

    private fun submitMessage() {
        if (isSending) return
        val text = inputArea.text.trim()
        if (text.isBlank()) return
        val model = activeModel
        if (model == null || model.apiKey.isBlank() || model.model.isBlank()) {
            appendSystemMessage("请先配置模型名称与 API Key，再发送消息。")
            return
        }

        inputArea.text = ""
        appendMessage(AiRole.USER, text)
        conversation.add(AiMessage(AiRole.USER, text))

        val assistantRow = appendMessage(AiRole.ASSISTANT, "正在思考…")
        isSending = true
        updateInputState()

        val history = conversation.toList()

        Thread {
            val result = client.send(
                modelConfig = model,
                messages = history,
                onDelta = if (model.stream) { delta ->
                    SwingUtilities.invokeLater {
                        if (assistantRow.text == "正在思考…") {
                            assistantRow.text = ""
                        }
                        assistantRow.text += delta
                        assistantRow.textArea.text = assistantRow.text
                        scrollToBottom()
                    }
                } else {
                    null
                }
            )

            SwingUtilities.invokeLater {
                when (result) {
                    is AiChatResult.Success -> {
                        assistantRow.text = result.text
                        assistantRow.textArea.text = result.text
                        conversation.add(AiMessage(AiRole.ASSISTANT, result.text))
                    }

                    is AiChatResult.Error -> {
                        assistantRow.text = "请求失败：${result.message}"
                        assistantRow.textArea.text = assistantRow.text
                        assistantRow.isError = true
                        assistantRow.bubble.setErrorState(true)
                        applyMessageColors()
                    }
                }
                isSending = false
                updateInputState()
                scrollToBottom()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun appendSystemMessage(text: String) {
        val row = appendMessage(AiRole.ASSISTANT, text)
        row.isError = true
        row.bubble.setErrorState(true)
        applyMessageColors()
    }

    private fun appendMessage(role: AiRole, text: String): MessageRow {
        val bubble = MessageBubble(role)
        val area = JTextArea(text).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
            font = font.deriveFont(Font.PLAIN, 12f)
        }

        val header = JLabel(if (role == AiRole.USER) "你" else "Agent").apply {
            font = font.deriveFont(Font.BOLD, 10f)
        }

        val card = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
            add(header)
            add(Box.createVerticalStrut(4))
            add(area)
        }

        bubble.add(card, BorderLayout.CENTER)

        val row = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
            if (role == AiRole.USER) {
                add(Box.createHorizontalGlue())
                add(bubble)
            } else {
                add(bubble)
                add(Box.createHorizontalGlue())
            }
        }

        messageRows.add(MessageRow(role, bubble, header, area, text))
        messageList.add(row)
        messageList.add(Box.createVerticalStrut(4))
        updateEmptyState()
        applyMessageColors()
        scrollToBottom()

        return messageRows.last()
    }

    private fun resetConversation() {
        clearConversation()
        appendMessage(AiRole.ASSISTANT, "你好！可以直接描述需求，我会给出步骤与建议。")
    }

    private fun clearConversation() {
        conversation.clear()
        messageRows.clear()
        messageList.removeAll()
        updateEmptyState()
        messageList.revalidate()
        messageList.repaint()
    }

    private fun updateEmptyState() {
        if (messageRows.isEmpty()) {
            bodyCard.show(bodyPanel, "empty")
        } else {
            bodyCard.show(bodyPanel, "messages")
        }
    }

    private fun reloadModels() {
        configStore.ensureTemplate()
        val settings = configStore.load()
        val options = settings.models.map { model ->
            val label = buildModelLabel(model)
            ModelOption(model.id, label, model)
        }

        modelOptions = if (options.isEmpty()) {
            listOf(ModelOption("", "未配置模型", null))
        } else {
            options
        }

        modelSelector.removeAllItems()
        modelOptions.forEach { modelSelector.addItem(it) }

        val activeId = settings.activeModelId
        val activeOption = modelOptions.firstOrNull { it.id == activeId } ?: modelOptions.firstOrNull()
        if (activeOption != null) {
            modelSelector.selectedItem = activeOption
            activeModel = activeOption.config
        }

        updateInputState()
        updateEmptyState()
    }

    private fun buildModelLabel(model: AiModelConfig): String {
        val missing = model.apiKey.isBlank() || model.model.isBlank()
        return if (missing) "${model.name} (未配置)" else model.name
    }

    private fun openConfigFile() {
        val file = configStore.ensureTemplate()
        openFile(file)
    }

    private fun openFile(file: File) {
        runCatching {
            java.awt.Desktop.getDesktop().open(file)
        }.onFailure {
            runCatching {
                java.awt.Desktop.getDesktop().open(file.parentFile)
            }
        }
    }

    private fun updateInputState() {
        val model = activeModel
        val ready = !isSending && model != null && model.apiKey.isNotBlank() && model.model.isNotBlank()
        sendButton.isEnabled = ready
        inputArea.isEditable = !isSending
        modelSelector.isEnabled = !isSending
        newChatButton.isEnabled = !isSending
        reloadButton.isEnabled = !isSending
        configButton.isEnabled = !isSending
        emptyReloadButton.isEnabled = !isSending
        emptyConfigButton.isEnabled = !isSending
        statusLabel.text = when {
            isSending -> "正在生成…"
            model == null -> "未配置模型"
            model.apiKey.isBlank() -> "缺少 API Key"
            model.model.isBlank() -> "缺少模型名称"
            else -> "Enter 发送 / Shift+Enter 换行"
        }
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            messageList.revalidate()
            messageScroll.verticalScrollBar.value = messageScroll.verticalScrollBar.maximum
        }
    }

    private fun applyMessageColors() {
        val theme = ThemeManager.currentTheme
        messageRows.forEach { row ->
            row.textArea.foreground = when {
                row.isError -> theme.onPrimary
                row.role == AiRole.USER -> theme.onPrimaryContainer
                else -> theme.onSurface
            }
            row.headerLabel.foreground = if (row.isError) theme.onPrimary else theme.onSurfaceVariant
        }
    }

    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        background = theme.sidebarBackground

        headerTitle.apply {
            foreground = theme.onSurface
            font = font.deriveFont(Font.BOLD, 14f)
        }
        headerSubtitle.apply {
            foreground = theme.onSurfaceVariant
            font = font.deriveFont(Font.PLAIN, 11f)
        }
        modelLabel.foreground = theme.onSurfaceVariant
        modelLabel.font = modelLabel.font.deriveFont(Font.PLAIN, 11f)

        headerPanel.apply {
            background = theme.sidebarBackground
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.statusBarSeparator),
                BorderFactory.createEmptyBorder(14, 14, 10, 14)
            )
        }
        inputContainer.apply {
            background = theme.sidebarBackground
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.statusBarSeparator),
                BorderFactory.createEmptyBorder(10, 14, 14, 14)
            )
        }
        inputWrapper.border = BorderFactory.createLineBorder(theme.outline, 1, true)

        listOf(reloadButton, configButton, newChatButton, emptyConfigButton, emptyReloadButton).forEach { btn ->
            btn.background = theme.surface
            btn.foreground = theme.onSurface
            btn.isFocusPainted = false
        }
        sendButton.apply {
            background = theme.primary
            foreground = theme.onPrimary
            isFocusPainted = false
        }
        modelSelector.apply {
            background = theme.surface
            foreground = theme.onSurface
        }

        inputArea.apply {
            foreground = theme.onSurface
            caretColor = theme.onSurface
        }

        statusLabel.apply {
            foreground = theme.onSurfaceVariant
            font = font.deriveFont(Font.PLAIN, 11f)
        }

        messageScroll.viewport.background = theme.sidebarBackground
        emptyState.background = theme.sidebarBackground

        applyMessageColors()
        revalidate()
        repaint()
    }

    private fun createEmptyState(): JComponent {
        val title = JLabel("配置模型后开始对话").apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }
        val subtitle = JLabel("支持多个模型，切换无需重启。")
        emptyConfigButton.addActionListener { openConfigFile() }
        emptyReloadButton.addActionListener { reloadModels() }
        val actionRow = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(emptyConfigButton)
            add(Box.createHorizontalStrut(8))
            add(emptyReloadButton)
        }

        return JPanel().apply {
            isOpaque = true
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(60, 16, 16, 16)
            add(title)
            add(Box.createVerticalStrut(6))
            add(subtitle)
            add(Box.createVerticalStrut(14))
            add(actionRow)
        }
    }

    private class RoundedPanel(
        private val radius: Int,
        private val backgroundProvider: () -> Color
    ) : JPanel() {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = backgroundProvider()
                g2.fillRoundRect(0, 0, width, height, radius, radius)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    private class MessageBubble(private val role: AiRole) : JPanel(BorderLayout()) {
        private var isError = false

        init {
            isOpaque = false
        }

        fun setErrorState(value: Boolean) {
            isError = value
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val theme = ThemeManager.currentTheme
                val background = when {
                    isError -> theme.error
                    role == AiRole.USER -> theme.primaryContainer
                    else -> theme.cardBackground
                }
                val borderColor = when {
                    isError -> theme.error
                    role == AiRole.USER -> theme.primary
                    else -> theme.outline
                }

                g2.color = background
                g2.fillRoundRect(0, 0, width, height, 14, 14)
                g2.color = borderColor
                g2.drawRoundRect(0, 0, width - 1, height - 1, 14, 14)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }

        override fun getMaximumSize(): Dimension {
            return Dimension(Int.MAX_VALUE, super.getMaximumSize().height)
        }
    }
}
