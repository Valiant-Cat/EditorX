package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel

/**
 * 展示默认快捷键（暂不提供编辑功能）。
 */
class KeymapPanel : JPanel(BorderLayout()) {

    private data class ShortcutItem(
        val actionZh: String,
        val actionEn: String,
        val shortcut: String,
        val descriptionZh: String,
        val descriptionEn: String,
    )

    private fun isEnglish(): Boolean = I18n.locale().language == java.util.Locale.ENGLISH.language

    private val shortcuts by lazy {
        listOf(
            ShortcutItem("全局搜索", "Global Search", if (isEnglish()) "Double Shift" else "双击Shift", "打开全局搜索对话框", "Open global search dialog"),
            ShortcutItem("查找", "Find", keyStroke(KeyEvent.VK_F), "聚焦顶部搜索栏", "Focus search bar"),
            ShortcutItem("替换", "Replace", keyStroke(KeyEvent.VK_R), "展开替换行", "Expand replace row"),
            ShortcutItem("保存文件", "Save File", keyStroke(KeyEvent.VK_S), "保存当前编辑内容", "Save current content"),
            ShortcutItem("关闭标签页", "Close Tab", keyStroke(KeyEvent.VK_W), "关闭当前标签页", "Close active tab"),
            ShortcutItem("格式化文件", "Format File", formatKeyStroke(), "格式化当前文件", "Format current file"),
            ShortcutItem("打开设置", "Open Settings", keyStroke(KeyEvent.VK_COMMA), "打开设置对话框", "Open settings dialog"),
        )
    }

    private val tableModel = object : AbstractTableModel() {
        override fun getRowCount(): Int = shortcuts.size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(column: Int): String = when (column) {
            0 -> if (isEnglish()) "Action" else "功能"
            1 -> "Shortcut"
            else -> if (isEnglish()) "Description" else "说明"
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val item = shortcuts[rowIndex]
            val english = isEnglish()
            return when (columnIndex) {
                0 -> if (english) item.actionEn else item.actionZh
                1 -> item.shortcut
                else -> if (english) item.descriptionEn else item.descriptionZh
            }
        }
    }

    private val headerLabel = JLabel()
    private val hintLabel = JLabel()
    private val noteButton = JButton()
    private val exportButton = JButton()

    private val table = JTable(tableModel).apply {
        rowHeight = 28
        setShowGrid(false)
        tableHeader.reorderingAllowed = false
    }

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        headerLabel.font = headerLabel.font.deriveFont(Font.BOLD, 16f)
        headerLabel.border = BorderFactory.createEmptyBorder(0, 0, 12, 0)

        hintLabel.horizontalAlignment = SwingConstants.LEFT
        hintLabel.border = BorderFactory.createEmptyBorder(0, 0, 12, 0)

        noteButton.isEnabled = false
        exportButton.isEnabled = false

        val buttonBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(noteButton)
            add(exportButton)
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
        tableModel.fireTableDataChanged()
        applyTexts()
    }

    private fun applyTexts() {
        headerLabel.text = I18n.translate(I18nKeys.Settings.KEYMAP_TITLE)
        hintLabel.text = I18n.translate(I18nKeys.Settings.KEYMAP_HINT)
        noteButton.text = I18n.translate(I18nKeys.Settings.ADD_NOTE)
        noteButton.toolTipText = I18n.translate(I18nKeys.Settings.ADD_NOTE_TOOLTIP)
        exportButton.text = I18n.translate(I18nKeys.Settings.EXPORT)
        exportButton.toolTipText = I18n.translate(I18nKeys.Settings.EXPORT_TOOLTIP)
    }

    companion object {
        private fun keyStroke(keyCode: Int, modifiers: Int = 0): String {
            val mask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
            val combo = mask or modifiers
            val modText = KeyEvent.getModifiersExText(combo)
            val keyText = KeyEvent.getKeyText(keyCode)
            return "$modText+$keyText"
        }

        private fun formatKeyStroke(): String {
            val isMac = System.getProperty("os.name").lowercase().contains("mac")
            val modifiers = if (isMac) {
                InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK
            } else {
                InputEvent.ALT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK
            }
            val modText = KeyEvent.getModifiersExText(modifiers)
            val keyText = KeyEvent.getKeyText(KeyEvent.VK_L)
            return "$modText+$keyText"
        }
    }
}
