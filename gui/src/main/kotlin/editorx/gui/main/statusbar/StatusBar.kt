package editorx.gui.main.statusbar

import editorx.gui.core.ThemeManager
import editorx.gui.main.MainWindow
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class StatusBar(private val mainWindow: MainWindow) : JPanel() {
    private val statusLabel = JLabel("就绪").apply {
        font = font.deriveFont(Font.PLAIN, 12f)
    }

    private val fileInfoLabel = JLabel("").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
    }

    private var currentMessageTimer: Timer? = null

    // VCS Widget
    private val vcsWidget = VcsWidget(mainWindow.guiContext.workspace)

    /////////////////////
    // 右侧子组件
    /////////////////////

    // 进度条
    private val progressLabel = JLabel("").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        isVisible = false
    }
    private val progressBar = JProgressBar().apply {
        isStringPainted = true
        isVisible = false
        preferredSize = Dimension(150, 6)
        maximumSize = Dimension(150, 6) // 限制最大宽度
    }
    private val progressCancelButton = JButton().apply {
        isVisible = false
        isFocusable = false
        margin = java.awt.Insets(1, 4, 1, 4)
        preferredSize = Dimension(16, 16)
        isBorderPainted = false
        isContentAreaFilled = false
        toolTipText = "取消"
        // 使用系统默认的关闭图标
        icon = UIManager.getIcon("InternalFrame.closeIcon") ?: createCloseIcon()

        addActionListener { onProgressCancel?.invoke() }
    }
    private var progressLastUpdateTime = 0L
    private val progressUpdateThrottleMs = 50L // 限制更新频率为每50ms最多一次
    private var onProgressCancel: (() -> Unit)? = null

    // 行号和列号
    private val lineColumnLabel = JLabel("").apply {
        toolTipText = "转到行/列"
        font = font.deriveFont(Font.PLAIN, 12f)
        isVisible = false  // 初始状态隐藏
        verticalAlignment = SwingConstants.CENTER

        // 设置最小高度以填充满状态栏
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                this@apply.isOpaque = true
                this@apply.background = ThemeManager.currentTheme.statusBarHoverBackground
                this@apply.repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                this@apply.isOpaque = false
                this@apply.repaint()
            }
        })
    }

    init {
        // 初始状态栏布局
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        updateTheme()
        preferredSize = Dimension(0, 28)

        // 安装子组件
        add(Box.createHorizontalStrut(12))
        setupLeftComponents()
        add(Box.createHorizontalGlue())
        setupRightComponents()
        add(Box.createHorizontalStrut(12))

        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }
    }

    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        background = theme.statusBarBackground
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, theme.statusBarSeparator),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)
        )
        statusLabel.foreground = theme.statusBarForeground
        fileInfoLabel.foreground = theme.statusBarSecondaryForeground
        progressLabel.foreground = theme.statusBarSecondaryForeground
        lineColumnLabel.foreground = theme.statusBarSecondaryForeground
        revalidate()
        repaint()
    }

    private fun setupLeftComponents() {
//        add(statusLabel)
//        add(Box.createHorizontalStrut(8))
//        add(fileInfoLabel)
//        add(Box.createHorizontalStrut(12))

        // VCS Widget
        add(vcsWidget)
    }

    private fun setupRightComponents() {
        add(progressLabel)
        add(Box.createHorizontalStrut(8))
        add(progressBar)
        add(Box.createHorizontalStrut(4))
        add(progressCancelButton)
        add(Box.createHorizontalStrut(8))
        add(lineColumnLabel)
    }

    fun setMessage(msg: String, persistent: Boolean = false) {
        statusLabel.text = msg
        if (!persistent) {
            currentMessageTimer?.stop()
            currentMessageTimer =
                Timer(3000) {
                    statusLabel.text = "就绪"
                    statusLabel.foreground = ThemeManager.currentTheme.statusBarForeground
                }.apply {
                    isRepeats = false
                    start()
                }
        }
    }


    fun setFileInfo(fileName: String, fileSize: String? = null, encoding: String? = null) {
        val info = buildString {
            append(fileName)
            fileSize?.let { append(" ($it)") }
            encoding?.let { append(" [$it]") }
        }
        fileInfoLabel.text = info
    }

    fun setLineColumn(line: Int, column: Int) {
        lineColumnLabel.text = "行 $line, 列 $column"
        lineColumnLabel.isVisible = true
    }

    fun hideLineColumn() {
        lineColumnLabel.isVisible = false
    }

    fun showProgress(
        message: String,
        indeterminate: Boolean = true,
        cancellable: Boolean = false,
        onCancel: (() -> Unit)? = null,
        maximum: Int = 100
    ) {
        progressBar.apply {
            isVisible = true
            isIndeterminate = indeterminate
            string = "" // 进度条本身不显示文本
            setMaximum(maximum)
            if (indeterminate) {
                // 不确定性进度条：使用来回动画，不设置具体值
                value = 0
            }
        }
        // 在进度标签中显示进度信息
        progressLabel.apply {
            text = message
            isVisible = true
        }
        progressCancelButton.isVisible = cancellable
        onProgressCancel = onCancel
        // 只在首次显示进度时才重新布局
        if (!progressBar.isVisible) {
            revalidate()
        }
        // 只重绘进度相关组件，不重绘整个状态栏
        progressLabel.repaint()
        progressBar.repaint()
        progressCancelButton.repaint()
    }

    fun updateProgress(value: Int, message: String) {
        val currentTime = System.currentTimeMillis()

        // 防抖：限制更新频率
        if (currentTime - progressLastUpdateTime < progressUpdateThrottleMs) {
            return
        }
        progressLastUpdateTime = currentTime

        progressBar.apply {
            isVisible = true
            isIndeterminate = false // 确定性进度条
            this.value = minOf(value, maximum) // 确保不超过最大值
            string = "" // 进度条不显示文本
        }
        // 在进度标签中显示进度信息
        progressLabel.apply {
            val percentage =
                if (progressBar.maximum > 0) {
                    (value * 100) / progressBar.maximum
                } else {
                    0
                }
            text = "$message ($percentage%)"
            isVisible = true
        }
        // 只重绘进度条，不重绘整个状态栏
        progressBar.repaint()
    }

    fun hideProgress() {
        progressBar.isVisible = false
        progressCancelButton.isVisible = false
        progressLabel.isVisible = false
        onProgressCancel = null
        statusLabel.text = "就绪" // 恢复默认状态
        // 只在隐藏进度时才重新布局
        revalidate()
        // 只重绘相关组件
        progressLabel.repaint()
        progressBar.repaint()
        progressCancelButton.repaint()
        statusLabel.repaint()
    }

    fun clear() {
        statusLabel.text = "就绪"
        fileInfoLabel.text = ""
        lineColumnLabel.text = ""
        hideLineColumn()
        hideProgress()
    }

    fun showError(message: String) {
        statusLabel.apply {
            text = "错误: $message"
            foreground = Color.RED
        }
        Timer(5000) {
            statusLabel.foreground = ThemeManager.currentTheme.statusBarForeground
        }.apply {
            isRepeats = false
            start()
        }
    }

    fun showWarning(message: String) {
        statusLabel.apply {
            text = "警告: $message"
            foreground = Color.ORANGE
        }
        Timer(3000) {
            statusLabel.foreground = ThemeManager.currentTheme.statusBarForeground
        }.apply {
            isRepeats = false
            start()
        }
    }

    fun showSuccess(message: String) {
        statusLabel.apply {
            text = "成功: $message"
            foreground = Color.GREEN
        }
        Timer(2000) {
            statusLabel.foreground = ThemeManager.currentTheme.statusBarForeground
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun createCloseIcon(): Icon {
        return object : Icon {
            override fun getIconWidth(): Int = 12
            override fun getIconHeight(): Int = 12

            override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics, x: Int, y: Int) {
                val g2 = g.create() as java.awt.Graphics2D
                try {
                    g2.setRenderingHint(
                        java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                    )
                    g2.color = Color.GRAY
                    g2.stroke = java.awt.BasicStroke(1.5f)
                    // 绘制X形状
                    g2.drawLine(x + 2, y + 2, x + 10, y + 10)
                    g2.drawLine(x + 10, y + 2, x + 2, y + 10)
                } finally {
                    g2.dispose()
                }
            }
        }
    }

    /**
     * 更新 VCS Widget 的显示内容（显示 git 分支或"版本控制"）
     */
    fun updateVcsDisplay() {
        vcsWidget.updateDisplay()
    }
}
