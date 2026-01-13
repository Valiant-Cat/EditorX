package editorx.gui.update

import editorx.gui.MainWindow
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

object UpdateDialog {
    fun confirmUpdate(mainWindow: MainWindow, info: UpdateManager.UpdateAvailable): Boolean {
        val notes = (info.releaseNotes ?: "").trim()
        val notesArea = JTextArea(notes.ifEmpty { "(无更新说明)" }).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }
        val scroll = JScrollPane(notesArea).apply {
            preferredSize = Dimension(560, 280)
        }

        val header = JLabel(
            "<html><b>发现新版本</b><br/>当前版本：${info.currentVersion}<br/>最新版本：${info.latestVersion}</html>"
        )

        val panel = JPanel(BorderLayout(0, 10)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            add(header, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }

        val options = arrayOf("取消", "更新并重启")
        val result = JOptionPane.showOptionDialog(
            mainWindow,
            panel,
            "检查更新",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[1]
        )
        return result == 1
    }

    fun confirmRestart(mainWindow: MainWindow, message: String): Boolean {
        val options = arrayOf("稍后", "立即重启")
        val result = JOptionPane.showOptionDialog(
            mainWindow,
            message,
            "更新准备完成",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[1]
        )
        return result == 1
    }

    fun showDownloadingDialog(mainWindow: MainWindow, title: String): DownloadingDialog {
        return DownloadingDialog(mainWindow, title)
    }

    class DownloadingDialog(owner: MainWindow, title: String) {
        private val label = JLabel("准备下载…")
        private val bar = javax.swing.JProgressBar(0, 100).apply {
            isStringPainted = true
            value = 0
        }
        private val cancelButton = JButton("取消")
        private var onCancel: (() -> Unit)? = null

        private val dialog = JDialog(owner, title, false).apply {
            contentPane = JPanel(BorderLayout(0, 8)).apply {
                border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
                add(label, BorderLayout.NORTH)
                add(bar, BorderLayout.CENTER)
                add(cancelButton, BorderLayout.SOUTH)
            }
            isResizable = false
            pack()
            setLocationRelativeTo(owner)
        }

        init {
            cancelButton.addActionListener { onCancel?.invoke() }
        }

        fun setOnCancel(action: (() -> Unit)?) {
            onCancel = action
        }

        fun show() {
            dialog.isVisible = true
        }

        fun close() {
            dialog.isVisible = false
            dialog.dispose()
        }

        fun update(message: String, percent: Int?) {
            label.text = message
            if (percent == null) {
                bar.isIndeterminate = true
                bar.string = message
            } else {
                bar.isIndeterminate = false
                bar.value = percent.coerceIn(0, 100)
                bar.string = "${bar.value}%"
            }
        }
    }
}
