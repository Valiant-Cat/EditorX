package editorx.gui.workbench.agent

import editorx.gui.MainWindow
import editorx.gui.theme.ThemeManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class AgentPanel(private val mainWindow: MainWindow) : JPanel(BorderLayout()) {

    private data class MessageRow(
        val fromUser: Boolean,
        val bubble: BubblePanel,
        val textArea: JTextArea
    )

    private val messageRows = mutableListOf<MessageRow>()

    private val headerTitle = JLabel("Agent")
    private val headerStatus = JLabel("Beta")
    private val newChatButton = JButton("新对话")
    private val clearButton = JButton("清空")

    private val messageList = JPanel()
    private val messageScroll = JScrollPane(messageList)
    private val emptyState = createEmptyState()

    private val bodyCard = java.awt.CardLayout()
    private val bodyPanel = JPanel(bodyCard)

    private val inputArea = JTextArea(3, 20)
    private val sendButton = JButton("发送")

    init {
        isOpaque = true
        layout = BorderLayout()
        buildHeader()
        buildBody()
        buildInput()
        updateTheme()

        ThemeManager.addThemeChangeListener { updateTheme() }
    }

    private fun buildHeader() {
        val titleRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(headerTitle, BorderLayout.WEST)
            add(
                JPanel().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    add(headerStatus)
                },
                BorderLayout.EAST
            )
        }

        val actionRow = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(newChatButton)
            add(Box.createHorizontalStrut(6))
            add(clearButton)
            add(Box.createHorizontalGlue())
        }

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(12, 12, 8, 12)
            add(titleRow, BorderLayout.NORTH)
            add(Box.createVerticalStrut(6), BorderLayout.CENTER)
            add(actionRow, BorderLayout.SOUTH)
        }

        add(header, BorderLayout.NORTH)

        newChatButton.addActionListener { resetConversation() }
        clearButton.addActionListener { clearConversation() }
    }

    private fun buildBody() {
        messageList.layout = BoxLayout(messageList, BoxLayout.Y_AXIS)
        messageList.isOpaque = false

        messageScroll.apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
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
        val inputContainer = JPanel(BorderLayout()).apply {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(10, 12, 12, 12)
        }

        inputArea.apply {
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
            font = font.deriveFont(Font.PLAIN, 12f)
        }

        val inputWrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.currentTheme.outline, 1, true),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
            )
            add(inputArea, BorderLayout.CENTER)
        }

        val actions = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(Box.createHorizontalGlue())
            add(sendButton)
        }

        inputContainer.add(inputWrapper, BorderLayout.CENTER)
        inputContainer.add(Box.createVerticalStrut(8), BorderLayout.NORTH)
        inputContainer.add(actions, BorderLayout.SOUTH)

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
        val text = inputArea.text.trim()
        if (text.isBlank()) return
        appendMessage(text, fromUser = true)
        inputArea.text = ""
        appendMessage("已收到，我会结合当前上下文给出建议。", fromUser = false)
    }

    private fun appendMessage(text: String, fromUser: Boolean) {
        val theme = ThemeManager.currentTheme
        val bubble = BubblePanel {
            if (fromUser) theme.primaryContainer else theme.cardBackground
        }

        val area = JTextArea(text).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = if (fromUser) theme.onPrimaryContainer else theme.onSurface
        }

        bubble.add(area, BorderLayout.CENTER)

        val row = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
            add(bubble, if (fromUser) BorderLayout.EAST else BorderLayout.WEST)
        }

        messageRows.add(MessageRow(fromUser, bubble, area))
        messageList.add(row)
        messageList.add(Box.createVerticalStrut(4))
        updateEmptyState()

        SwingUtilities.invokeLater {
            messageList.revalidate()
            messageScroll.verticalScrollBar.value = messageScroll.verticalScrollBar.maximum
        }
    }

    private fun resetConversation() {
        clearConversation()
        appendMessage("你好！告诉我你想做什么，我会给出步骤与建议。", fromUser = false)
    }

    private fun clearConversation() {
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

    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        background = theme.sidebarBackground

        headerTitle.apply {
            foreground = theme.onSurface
            font = font.deriveFont(Font.BOLD, 13f)
        }
        headerStatus.apply {
            foreground = theme.onSurfaceVariant
            font = font.deriveFont(Font.PLAIN, 11f)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.outline, 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
            )
        }

        newChatButton.apply {
            background = theme.surface
            foreground = theme.onSurface
            isFocusPainted = false
        }
        clearButton.apply {
            background = theme.surface
            foreground = theme.onSurfaceVariant
            isFocusPainted = false
        }
        sendButton.apply {
            background = theme.primary
            foreground = theme.onPrimary
            isFocusPainted = false
        }

        messageScroll.viewport.background = theme.sidebarBackground

        messageRows.forEach { row ->
            row.textArea.foreground = if (row.fromUser) theme.onPrimaryContainer else theme.onSurface
            row.bubble.repaint()
        }

        inputArea.apply {
            foreground = theme.onSurface
            caretColor = theme.onSurface
        }

        revalidate()
        repaint()
    }

    private fun createEmptyState(): JComponent {
        val title = JLabel("开始一个新的对话").apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }
        val subtitle = JLabel("在这里向 Agent 提问、总结或生成代码。")

        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(80, 16, 16, 16)
            add(title)
            add(Box.createVerticalStrut(8))
            add(subtitle)
        }
    }

    private class BubblePanel(private val backgroundProvider: () -> Color) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        }

        override fun getPreferredSize(): Dimension {
            val base = super.getPreferredSize()
            return Dimension(base.width.coerceAtLeast(120), base.height)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = backgroundProvider()
                g2.fillRoundRect(0, 0, width, height, 12, 12)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }
}
