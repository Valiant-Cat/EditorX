package editorx.gui.workbench.statusbar

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.gui.theme.ThemeManager
import editorx.gui.MainWindow
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.SwingUtilities.invokeLater

class StatusBar(private val mainWindow: MainWindow) : JPanel() {
    private val statusLabel = JLabel().apply {
        font = font.deriveFont(Font.PLAIN, 12f)
        text = I18n.translate(I18nKeys.Status.READY)
    }

    private val fileInfoLabel = JLabel("").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
    }

    private var currentMessageTimer: Timer? = null

    /////////////////////
    // 右侧子组件
    /////////////////////

    // 进度条
    private val progressLabel = JLabel("").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        isVisible = false
    }

    private val tasksButton = JButton("0").apply {
        isVisible = false
        isFocusable = false
        margin = java.awt.Insets(1, 6, 1, 6)
        isBorderPainted = true
        isContentAreaFilled = false
        toolTipText = "后台任务"
        addActionListener { showTasksPopup() }
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
        toolTipText = I18n.translate(I18nKeys.Status.CANCEL)
        // 使用系统默认的关闭图标
        icon = UIManager.getIcon("InternalFrame.closeIcon") ?: createCloseIcon()

        addActionListener { onProgressCancel?.invoke() }
    }
    private var progressLastUpdateTime = 0L
    private val progressUpdateThrottleMs = 50L // 限制更新频率为每50ms最多一次
    private var onProgressCancel: (() -> Unit)? = null

    @JvmInline
    value class ProgressHandle(val id: Long)

    private data class ProgressTask(
        val id: Long,
        var message: String,
        var indeterminate: Boolean,
        var cancellable: Boolean,
        var maximum: Int,
        var value: Int,
        var onCancel: (() -> Unit)?,
    )

    private val taskLock = Any()
    private var nextTaskId = 1L
    private val tasks = LinkedHashMap<Long, ProgressTask>()
    private var pinnedTaskId: Long? = null
    private var lastUpdatedTaskId: Long? = null
    private var legacyHandle: ProgressHandle? = null

    // 行号和列号
    private val lineColumnLabel = JLabel("").apply {
        toolTipText = I18n.translate(I18nKeys.Status.GOTO_LINE_COLUMN)
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
        setupLeftComponents()
        add(Box.createHorizontalGlue())
        setupRightComponents()

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
        add(mainWindow.navigationBar)
    }

    private fun setupRightComponents() {
        add(tasksButton)
        add(Box.createHorizontalStrut(6))
        add(progressLabel)
        add(Box.createHorizontalStrut(8))
        add(progressBar)
        add(Box.createHorizontalStrut(4))
        add(progressCancelButton)
        add(Box.createHorizontalStrut(8))
        add(lineColumnLabel)
    }

    fun setMessage(msg: String) {
        statusLabel.text = msg
        currentMessageTimer?.stop()
        currentMessageTimer =
            Timer(3000) {
                statusLabel.text = I18n.translate(I18nKeys.Status.READY)
                statusLabel.foreground = ThemeManager.currentTheme.statusBarForeground
            }.apply {
                isRepeats = false
                start()
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
        lineColumnLabel.text = I18n.translate(I18nKeys.Status.LINE_COLUMN).format(line, column)
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
        val handle = synchronized(taskLock) {
            val existing = legacyHandle
            if (existing != null && tasks.containsKey(existing.id)) {
                existing
            } else {
                val h = beginProgressTaskLocked(message, indeterminate, cancellable, onCancel, maximum)
                legacyHandle = h
                h
            }
        }
        updateProgressTask(handle, message, indeterminate, cancellable, onCancel, maximum)
    }

    fun beginProgressTask(
        message: String,
        indeterminate: Boolean = true,
        cancellable: Boolean = false,
        onCancel: (() -> Unit)? = null,
        maximum: Int = 100
    ): ProgressHandle {
        val handle = synchronized(taskLock) {
            beginProgressTaskLocked(message, indeterminate, cancellable, onCancel, maximum)
        }
        refreshProgressUiAsync()
        return handle
    }

    fun updateProgressTask(
        handle: ProgressHandle,
        message: String,
        indeterminate: Boolean = true,
        cancellable: Boolean = false,
        onCancel: (() -> Unit)? = null,
        maximum: Int = 100
    ) {
        synchronized(taskLock) {
            val task = tasks[handle.id] ?: return
            task.message = message
            task.indeterminate = indeterminate
            task.cancellable = cancellable
            task.onCancel = onCancel
            if (maximum > 0) task.maximum = maximum
            if (indeterminate) task.value = 0
            lastUpdatedTaskId = task.id
        }
        refreshProgressUiAsync()
    }

    fun updateProgress(value: Int, message: String) {
        val handle = synchronized(taskLock) { legacyHandle } ?: return
        updateProgressTask(handle, value, message)
    }

    fun updateProgressTask(handle: ProgressHandle, value: Int, message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - progressLastUpdateTime < progressUpdateThrottleMs) return
        progressLastUpdateTime = currentTime

        synchronized(taskLock) {
            val task = tasks[handle.id] ?: return
            task.indeterminate = false
            task.value = value
            task.message = message
            lastUpdatedTaskId = task.id
        }
        refreshProgressUiAsync()
    }

    fun hideProgress() {
        val handle = synchronized(taskLock) { legacyHandle } ?: return
        endProgressTask(handle)
        synchronized(taskLock) { if (legacyHandle?.id == handle.id) legacyHandle = null }
    }

    fun endProgressTask(handle: ProgressHandle) {
        synchronized(taskLock) {
            tasks.remove(handle.id)
            if (pinnedTaskId == handle.id) pinnedTaskId = null
            if (lastUpdatedTaskId == handle.id) lastUpdatedTaskId = null
        }
        refreshProgressUiAsync()
    }

    private fun beginProgressTaskLocked(
        message: String,
        indeterminate: Boolean,
        cancellable: Boolean,
        onCancel: (() -> Unit)?,
        maximum: Int
    ): ProgressHandle {
        val id = nextTaskId++
        tasks[id] = ProgressTask(
            id = id,
            message = message,
            indeterminate = indeterminate,
            cancellable = cancellable,
            maximum = maximum,
            value = 0,
            onCancel = onCancel,
        )
        lastUpdatedTaskId = id
        return ProgressHandle(id)
    }

    private fun activeTask(): ProgressTask? {
        val id = pinnedTaskId ?: lastUpdatedTaskId ?: tasks.keys.lastOrNull()
        return if (id == null) null else tasks[id]
    }

    private fun refreshProgressUiAsync() {
        if (SwingUtilities.isEventDispatchThread()) {
            refreshProgressUi()
        } else {
            invokeLater { refreshProgressUi() }
        }
    }

    private fun refreshProgressUi() {
        val snapshot = synchronized(taskLock) {
            val task = activeTask()
            val size = tasks.size
            Triple(task, size, tasks.values.toList())
        }

        val active = snapshot.first
        val size = snapshot.second

        tasksButton.isVisible = size > 1
        tasksButton.text = size.toString()

        if (active == null) {
            progressBar.isVisible = false
            progressCancelButton.isVisible = false
            progressLabel.isVisible = false
            onProgressCancel = null
            revalidate()
            repaint()
            return
        }

        progressBar.apply {
            isVisible = true
            isIndeterminate = active.indeterminate
            string = ""
            setMaximum(active.maximum)
            value = if (active.indeterminate) 0 else minOf(active.value, maximum)
        }
        progressLabel.apply {
            text =
                if (active.indeterminate) {
                    active.message
                } else {
                    val percentage =
                        if (active.maximum > 0) {
                            (active.value * 100) / active.maximum
                        } else 0
                    "${active.message} ($percentage%)"
                }
            isVisible = true
        }
        progressCancelButton.isVisible = active.cancellable
        onProgressCancel = active.onCancel

        revalidate()
        repaint()
    }

    private fun showTasksPopup() {
        val button = tasksButton
        if (!button.isVisible) return

        val snapshot = synchronized(taskLock) {
            val currentPinned = pinnedTaskId
            tasks.values.map { it.copy() } to currentPinned
        }
        val list = snapshot.first
        val currentPinned = snapshot.second

        val popup = JPopupMenu()
        if (list.isEmpty()) return

        for (t in list) {
            val label =
                if (t.indeterminate) t.message
                else {
                    val percentage = if (t.maximum > 0) (t.value * 100) / t.maximum else 0
                    "${t.message} ($percentage%)"
                }
            val item = JCheckBoxMenuItem(label, currentPinned == t.id).apply {
                addActionListener {
                    synchronized(taskLock) { pinnedTaskId = t.id }
                    refreshProgressUiAsync()
                }
            }
            popup.add(item)
        }

        popup.addSeparator()
        popup.add(JMenuItem("显示最新任务").apply {
            addActionListener {
                synchronized(taskLock) { pinnedTaskId = null }
                refreshProgressUiAsync()
            }
        })

        popup.show(button, 0, button.height)
    }

    fun clear() {
        statusLabel.text = I18n.translate(I18nKeys.Status.READY)
        fileInfoLabel.text = ""
        lineColumnLabel.text = ""
        hideLineColumn()
        hideProgress()
    }

    fun showError(message: String) {
        statusLabel.apply {
            text = "${I18n.translate(I18nKeys.Status.ERROR)}: $message"
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
            text = "${I18n.translate(I18nKeys.Status.WARNING)}: $message"
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
            text = "${I18n.translate(I18nKeys.Status.SUCCESS)}: $message"
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
}
