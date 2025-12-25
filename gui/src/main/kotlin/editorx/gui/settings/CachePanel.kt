package editorx.gui.settings

import editorx.core.gui.GuiContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class CachePanel(private val guiEnv: GuiContext) : JPanel(BorderLayout()) {
    private data class CacheEntry(val name: String, val nameEn: String, val dir: File, val desc: String, val descEn: String)

    private val entries: List<CacheEntry> by lazy {
        val base = guiEnv.appDir
        listOf(
            CacheEntry("缓存内容", "Cache", File(base, "cache"), "工作区与插件生成缓存", "Workspace and plugin caches"),
            CacheEntry("日志文件", "Logs", File(base, "logs"), "运行日志，便于排查问题", "Runtime logs for troubleshooting"),
        )
    }

    private val headerLabel = JLabel()
    private val hintLabel = JLabel()
    private val refreshButton = JButton()
    private val clearButton = JButton()
    private val openButton = JButton()

    private val tableModel = object : AbstractTableModel() {
        private var sizes: List<Long> = entries.map { computeSize(it.dir) }

        fun refreshSizes() {
            sizes = entries.map { computeSize(it.dir) }
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = entries.size
        override fun getColumnCount(): Int = 4
        override fun getColumnName(column: Int): String = when (column) {
            0 -> if (isEnglish()) "Name" else "名称"
            1 -> if (isEnglish()) "Path" else "路径"
            2 -> if (isEnglish()) "Size" else "大小"
            else -> if (isEnglish()) "Description" else "说明"
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val entry = entries[rowIndex]
            val english = isEnglish()
            return when (columnIndex) {
                0 -> if (english) entry.nameEn else entry.name
                1 -> entry.dir.absolutePath
                2 -> readableSize(sizes[rowIndex])
                else -> if (english) entry.descEn else entry.desc
            }
        }
    }

    private val table = JTable(tableModel).apply {
        rowHeight = 28
        setShowGrid(false)
        tableHeader.reorderingAllowed = false
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        headerLabel.font = headerLabel.font.deriveFont(java.awt.Font.BOLD, 16f)
        headerLabel.border = BorderFactory.createEmptyBorder(0, 0, 12, 0)

        hintLabel.border = BorderFactory.createEmptyBorder(0, 0, 12, 0)

        refreshButton.addActionListener { refresh() }
        clearButton.addActionListener { clearSelected() }
        openButton.addActionListener { openSelectedDir() }

        val buttonBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(refreshButton)
            add(clearButton)
            add(openButton)
        }

        add(headerLabel, BorderLayout.NORTH)
        add(JScrollPane(table).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(12, 0, 0, 0)
                add(hintLabel, BorderLayout.NORTH)
                add(buttonBar, BorderLayout.CENTER)
            },
            BorderLayout.SOUTH
        )

        applyTexts()
    }

    fun refresh() {
        tableModel.refreshSizes()
        applyTexts()
    }

    private fun clearSelected() {
        val idx = table.selectedRow.takeIf { it >= 0 } ?: run {
            JOptionPane.showMessageDialog(
                this,
                if (isEnglish()) "Select an entry first." else "请选择要清理的缓存条目",
                infoTitle(),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        val entry = entries[idx]
        if (!entry.dir.exists()) {
                JOptionPane.showMessageDialog(
                    this,
                    if (isEnglish()) "Directory not found: ${entry.dir.absolutePath}" else "目录不存在：${entry.dir.absolutePath}",
                    infoTitle(),
                    JOptionPane.INFORMATION_MESSAGE
                )
            tableModel.refreshSizes()
            return
        }
        val confirm = JOptionPane.showConfirmDialog(
            this,
            if (isEnglish()) "Clear ${entry.nameEn}?\n${entry.dir.absolutePath}" else "确认清理 ${entry.name}？\n${entry.dir.absolutePath}",
            if (isEnglish()) "Clear Cache" else "清理缓存",
            JOptionPane.YES_NO_OPTION
        )
        if (confirm != JOptionPane.YES_OPTION) return

        val success = deleteRecursively(entry.dir)
        JOptionPane.showMessageDialog(
            this,
            if (success) {
                if (isEnglish()) "Cleared ${entry.nameEn}" else "已清理 ${entry.name}"
            } else {
                if (isEnglish()) "Failed to clear, files may be in use." else "清理失败，请检查文件是否被占用"
            },
            infoTitle(),
            if (success) JOptionPane.INFORMATION_MESSAGE else JOptionPane.WARNING_MESSAGE
        )
        tableModel.refreshSizes()
    }

    private fun openSelectedDir() {
        val idx = table.selectedRow.takeIf { it >= 0 } ?: run {
            JOptionPane.showMessageDialog(
                this,
                if (isEnglish()) "Select an entry first." else "请选择要打开的条目",
                infoTitle(),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        val entry = entries[idx]
        if (!entry.dir.exists()) {
            entry.dir.mkdirs()
        }
        runCatching {
            java.awt.Desktop.getDesktop().open(entry.dir)
        }.onFailure {
            JOptionPane.showMessageDialog(
                this,
                if (isEnglish()) "Unable to open: ${entry.dir.absolutePath}" else "无法打开目录：${entry.dir.absolutePath}",
                infoTitle(),
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun computeSize(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    private fun readableSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun deleteRecursively(dir: File): Boolean {
        if (!dir.exists()) return true
        return runCatching {
            dir.walkBottomUp().forEach { file ->
                if (!file.delete()) {
                    throw IllegalStateException("无法删除：${file.absolutePath}")
                }
            }
            true
        }.getOrElse { false }
    }

    private fun infoTitle(): String = if (isEnglish()) "Info" else "提示"
    private fun isEnglish(): Boolean = editorx.core.i18n.I18n.locale().language == java.util.Locale.ENGLISH.language

    private fun applyTexts() {
        if (isEnglish()) {
            headerLabel.text = "Cache"
            hintLabel.text = "Clear cache only when no background decompile tasks are running."
            refreshButton.text = "Refresh"
            refreshButton.toolTipText = null
            clearButton.text = "Clear Selected"
            clearButton.toolTipText = null
            openButton.text = "Open Folder"
            openButton.toolTipText = null
        } else {
            headerLabel.text = "缓存"
            hintLabel.text = "清理缓存前请确认不存在正在运行的反编译或插件任务。"
            refreshButton.text = "刷新"
            clearButton.text = "清理所选"
            openButton.text = "打开所在目录"
        }
    }
}
