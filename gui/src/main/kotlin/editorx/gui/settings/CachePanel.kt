package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.gui.GuiContext
import editorx.gui.ThemeManager
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
import javax.swing.table.DefaultTableCellRenderer

class CachePanel(private val guiEnv: GuiContext) : JPanel(BorderLayout()) {
    private data class CacheEntry(val name: String, val nameEn: String, val dir: File, val desc: String, val descEn: String)

    private val entries: List<CacheEntry> by lazy {
        val base = guiEnv.getAppDir()
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
    private var tableScrollPane: JScrollPane? = null

    private val tableModel = object : AbstractTableModel() {
        private var sizes: List<Long> = entries.map { computeSize(it.dir) }

        fun refreshSizes() {
            sizes = entries.map { computeSize(it.dir) }
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = entries.size
        override fun getColumnCount(): Int = 4
        override fun getColumnName(column: Int): String = when (column) {
            0 -> I18n.translate(I18nKeys.CacheTable.NAME)
            1 -> I18n.translate(I18nKeys.CacheTable.PATH)
            2 -> I18n.translate(I18nKeys.CacheTable.SIZE)
            else -> I18n.translate(I18nKeys.CacheTable.DESCRIPTION)
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val entry = entries[rowIndex]
            val english = I18n.locale().language == java.util.Locale.ENGLISH.language
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
        
        // 设置自定义单元格渲染器以应用主题颜色
        setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): java.awt.Component {
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                val theme = ThemeManager.currentTheme
                
                if (isSelected) {
                    c.background = theme.primaryContainer
                    c.foreground = theme.onPrimaryContainer
                } else {
                    c.background = theme.surface
                    c.foreground = theme.onSurface
                }
                c.isOpaque = true
                
                return c
            }
        })
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
            isOpaque = false
            add(refreshButton)
            add(clearButton)
            add(openButton)
        }

        tableScrollPane = JScrollPane(table).apply { 
            border = BorderFactory.createEmptyBorder()
        }

        add(headerLabel, BorderLayout.NORTH)
        add(tableScrollPane, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(12, 0, 0, 0)
                add(hintLabel, BorderLayout.NORTH)
                add(buttonBar, BorderLayout.CENTER)
            },
            BorderLayout.SOUTH
        )

        applyTexts()
        
        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }
        updateTheme()
    }
    
    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        
        // 更新面板背景
        background = theme.surface
        isOpaque = true
        
        // 更新标签颜色
        headerLabel.foreground = theme.onSurface
        hintLabel.foreground = theme.onSurfaceVariant
        
        // 更新表格颜色
        table.background = theme.surface
        table.foreground = theme.onSurface
        table.selectionBackground = theme.primaryContainer
        table.selectionForeground = theme.onPrimaryContainer
        table.gridColor = theme.outline
        table.isOpaque = true
        
        // 更新表格表头颜色
        table.tableHeader.background = theme.surfaceVariant
        table.tableHeader.foreground = theme.onSurfaceVariant
        table.tableHeader.isOpaque = true
        
        // 更新滚动面板颜色
        tableScrollPane?.let { scroll ->
            scroll.background = theme.surface
            scroll.viewport.background = theme.surface
            scroll.viewport.isOpaque = true
            scroll.border = BorderFactory.createEmptyBorder()
        }
        
        // 更新按钮颜色
        refreshButton.background = theme.surface
        refreshButton.foreground = theme.onSurface
        refreshButton.isOpaque = true
        clearButton.background = theme.surface
        clearButton.foreground = theme.onSurface
        clearButton.isOpaque = true
        openButton.background = theme.surface
        openButton.foreground = theme.onSurface
        openButton.isOpaque = true
        
        // 强制表格重新渲染
        table.repaint()
        table.tableHeader.repaint()
        
        repaint()
    }

    fun refresh() {
        tableModel.refreshSizes()
        applyTexts()
        updateTheme()
    }

    private fun clearSelected() {
        val idx = table.selectedRow.takeIf { it >= 0 } ?: run {
            JOptionPane.showMessageDialog(
                this,
                I18n.translate(I18nKeys.Dialog.SELECT_ENTRY_FIRST),
                I18n.translate(I18nKeys.Dialog.INFO),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        val entry = entries[idx]
        if (!entry.dir.exists()) {
                JOptionPane.showMessageDialog(
                    this,
                    I18n.translate(I18nKeys.Dialog.DIRECTORY_NOT_FOUND).format(entry.dir.absolutePath),
                    I18n.translate(I18nKeys.Dialog.INFO),
                    JOptionPane.INFORMATION_MESSAGE
                )
            tableModel.refreshSizes()
            return
        }
        val confirm = JOptionPane.showConfirmDialog(
            this,
            if (I18n.locale().language == java.util.Locale.ENGLISH.language) {
                "Clear ${entry.nameEn}?\n${entry.dir.absolutePath}"
            } else {
                "确认清理 ${entry.name}？\n${entry.dir.absolutePath}"
            },
            I18n.translate(I18nKeys.Dialog.CLEAR_CACHE),
            JOptionPane.YES_NO_OPTION
        )
        if (confirm != JOptionPane.YES_OPTION) return

        val success = deleteRecursively(entry.dir)
        val message = if (success) {
            if (I18n.locale().language == java.util.Locale.ENGLISH.language) {
                "${I18n.translate(I18nKeys.Dialog.CLEARED)} ${entry.nameEn}"
            } else {
                "${I18n.translate(I18nKeys.Dialog.CLEARED)} ${entry.name}"
            }
        } else {
            I18n.translate(I18nKeys.Dialog.CLEAR_FAILED)
        }
        JOptionPane.showMessageDialog(
            this,
            message,
            I18n.translate(I18nKeys.Dialog.INFO),
            if (success) JOptionPane.INFORMATION_MESSAGE else JOptionPane.WARNING_MESSAGE
        )
        tableModel.refreshSizes()
    }

    private fun openSelectedDir() {
        val idx = table.selectedRow.takeIf { it >= 0 } ?: run {
            JOptionPane.showMessageDialog(
                this,
                I18n.translate(I18nKeys.Dialog.SELECT_ENTRY_FIRST),
                I18n.translate(I18nKeys.Dialog.INFO),
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
                I18n.translate(I18nKeys.Dialog.UNABLE_TO_OPEN).format(entry.dir.absolutePath),
                I18n.translate(I18nKeys.Dialog.INFO),
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

    private fun applyTexts() {
        headerLabel.text = I18n.translate(I18nKeys.Settings.CACHE_TITLE)
        hintLabel.text = I18n.translate(I18nKeys.Settings.CACHE_HINT)
        refreshButton.text = I18n.translate(I18nKeys.Settings.REFRESH_CACHE)
        refreshButton.toolTipText = null
        clearButton.text = I18n.translate(I18nKeys.Settings.CLEAR_SELECTED)
        clearButton.toolTipText = null
        openButton.text = I18n.translate(I18nKeys.Settings.OPEN_FOLDER)
        openButton.toolTipText = null
    }
}
