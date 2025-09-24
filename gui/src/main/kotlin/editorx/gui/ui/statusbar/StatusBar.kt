package editorx.gui.ui.statusbar

import java.awt.Color
import editorx.gui.ui.MainWindow
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*

class StatusBar(private val mainWindow: MainWindow) : JPanel() {
    private val statusLabel = JLabel("就绪")
    private val fileInfoLabel = JLabel("")
    private val progressBar = JProgressBar()
    private val lineColumnLabel = JLabel("")
    private var currentMessageTimer: Timer? = null

    init { setupStatusBar() }

    private fun setupStatusBar() {
        layout = FlowLayout(FlowLayout.LEFT, 5, 2)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)
        )
        background = Color.WHITE
        preferredSize = Dimension(0, 25)
        setupComponents()
    }

    private fun setupComponents() {
        statusLabel.apply { font = font.deriveFont(Font.PLAIN, 12f); foreground = Color.BLACK }
        fileInfoLabel.apply { font = font.deriveFont(Font.PLAIN, 11f); foreground = Color.GRAY }
        lineColumnLabel.apply { font = font.deriveFont(Font.PLAIN, 11f); foreground = Color.GRAY }
        progressBar.apply { isStringPainted = true; isVisible = false; preferredSize = Dimension(100, 16) }
        add(statusLabel)
        add(JSeparator(JSeparator.VERTICAL))
        add(fileInfoLabel)
        add(JSeparator(JSeparator.VERTICAL))
        add(lineColumnLabel)
        add(JSeparator(JSeparator.VERTICAL))
        add(progressBar)
    }

    fun setMessage(msg: String, persistent: Boolean = false) {
        statusLabel.text = msg
        if (!persistent) {
            currentMessageTimer?.stop()
            currentMessageTimer = Timer(3000) { statusLabel.text = "就绪" }.apply { isRepeats = false; start() }
        }
    }

    fun setFileInfo(fileName: String, fileSize: String? = null, encoding: String? = null) {
        val info = buildString {
            append(fileName); fileSize?.let { append(" ($it)") }; encoding?.let { append(" [$it]") }
        }
        fileInfoLabel.text = info
    }

    fun setLineColumn(line: Int, column: Int) { lineColumnLabel.text = "行 $line, 列 $column" }

    fun showProgress(message: String, indeterminate: Boolean = true) {
        progressBar.apply { isVisible = true; isIndeterminate = indeterminate; string = message }
        revalidate(); repaint()
    }

    fun updateProgress(value: Int, message: String) {
        progressBar.apply { isVisible = true; isIndeterminate = false; this.value = value; string = message }
    }

    fun hideProgress() { progressBar.isVisible = false; revalidate(); repaint() }

    fun clear() { statusLabel.text = "就绪"; fileInfoLabel.text = ""; lineColumnLabel.text = ""; hideProgress() }

    fun showError(message: String) {
        statusLabel.apply { text = "错误: $message"; foreground = Color.RED }
        Timer(5000) { statusLabel.foreground = Color.BLACK }.apply { isRepeats = false; start() }
    }

    fun showWarning(message: String) {
        statusLabel.apply { text = "警告: $message"; foreground = Color.ORANGE }
        Timer(3000) { statusLabel.foreground = Color.BLACK }.apply { isRepeats = false; start() }
    }

    fun showSuccess(message: String) {
        statusLabel.apply { text = "成功: $message"; foreground = Color.GREEN }
        Timer(2000) { statusLabel.foreground = Color.BLACK }.apply { isRepeats = false; start() }
    }
}
