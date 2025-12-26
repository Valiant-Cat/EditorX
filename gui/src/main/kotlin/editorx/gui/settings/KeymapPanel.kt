package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.gui.core.ShortcutRegistry
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
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel

/**
 * 展示快捷键列表（从 ShortcutRegistry 获取）。
 */
class KeymapPanel : JPanel(BorderLayout()) {

    private data class ShortcutItem(
        val action: String, // 功能名称（已翻译）
        val shortcut: String, // 快捷键显示文本
        val description: String, // 描述（已翻译）
    )

    private fun isEnglish(): Boolean = I18n.locale().language == java.util.Locale.ENGLISH.language

    /**
     * 获取快捷键列表
     * 合并显示 ShortcutRegistry 中的快捷键和默认列表中的快捷键
     */
    private fun getShortcuts(): List<ShortcutItem> {
        val shortcuts = mutableListOf<ShortcutItem>()
        val addedIds = mutableSetOf<String>()
        
        // 从 ShortcutRegistry 获取已注册的快捷键
        ShortcutRegistry.getAllShortcuts().forEach { binding ->
            shortcuts.add(
                ShortcutItem(
                    action = getActionName(binding.id),
                    shortcut = formatKeyStroke(binding.keyStroke),
                    description = binding.displayDescription
                )
            )
            addedIds.add(binding.id)
        }
        
        // 添加默认列表中的快捷键（这些是 Editor 等组件中直接注册的）
        // 避免与 ShortcutManager 中的重复
        getDefaultShortcuts().forEach { defaultItem ->
            // 检查是否已经在 ShortcutRegistry 中存在（通过快捷键匹配）
            val exists = shortcuts.any { it.shortcut == defaultItem.shortcut }
            if (!exists) {
                shortcuts.add(defaultItem)
            }
        }
        
        return shortcuts.sortedBy { it.action }
    }
    
    /**
     * 根据快捷键 ID 获取功能名称
     */
    private fun getActionName(id: String): String {
        return when (id) {
            "global.search" -> I18n.translate(I18nKeys.Action.GLOBAL_SEARCH)
            "global.settings" -> I18n.translate(I18nKeys.Toolbar.SETTINGS)
            else -> id // 如果找不到翻译，使用 ID
        }
    }
    
    /**
     * 格式化 KeyStroke 为显示文本
     */
    private fun formatKeyStroke(keyStroke: KeyStroke): String {
        val modifiers = keyStroke.modifiers
        val keyCode = keyStroke.keyCode
        
        // 处理双击 Shift 的特殊情况
        if (keyCode == KeyEvent.VK_SHIFT && modifiers == 0) {
            return if (isEnglish()) "Double Shift" else "双击Shift"
        }
        
        // 转换为扩展掩码以正确显示
        val extendedModifiers = convertToExtendedModifiers(modifiers)
        val modText = KeyEvent.getModifiersExText(extendedModifiers)
        val keyText = KeyEvent.getKeyText(keyCode)
        
        return if (modText.isNotEmpty()) "$modText+$keyText" else keyText
    }
    
    /**
     * 将旧的修饰键掩码转换为扩展修饰键掩码
     */
    private fun convertToExtendedModifiers(oldModifiers: Int): Int {
        var extended = 0
        if ((oldModifiers and InputEvent.SHIFT_MASK) != 0) {
            extended = extended or InputEvent.SHIFT_DOWN_MASK
        }
        if ((oldModifiers and InputEvent.CTRL_MASK) != 0) {
            extended = extended or InputEvent.CTRL_DOWN_MASK
        }
        if ((oldModifiers and InputEvent.ALT_MASK) != 0) {
            extended = extended or InputEvent.ALT_DOWN_MASK
        }
        if ((oldModifiers and InputEvent.META_MASK) != 0) {
            extended = extended or InputEvent.META_DOWN_MASK
        }
        return extended
    }
    
    /**
     * 获取默认快捷键列表（当 ShortcutRegistry 不可用时使用）
     */
    private fun getDefaultShortcuts(): List<ShortcutItem> {
        val shortcutMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        return listOf(
            ShortcutItem(
                action = I18n.translate(I18nKeys.Action.GLOBAL_SEARCH),
                shortcut = if (isEnglish()) "Double Shift" else "双击Shift",
                description = I18n.translate(I18nKeys.Shortcut.GLOBAL_SEARCH)
            ),
            ShortcutItem(
                action = I18n.translate(I18nKeys.Action.FIND),
                shortcut = formatKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutMask)),
                description = I18n.translate(I18nKeys.Shortcut.FIND)
            ),
            ShortcutItem(
                action = I18n.translate(I18nKeys.Action.REPLACE),
                shortcut = formatKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcutMask)),
                description = I18n.translate(I18nKeys.Shortcut.REPLACE)
            ),
            ShortcutItem(
                action = I18n.translate(I18nKeys.Action.SAVE),
                shortcut = formatKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask)),
                description = I18n.translate(I18nKeys.Shortcut.SAVE)
            ),
            ShortcutItem(
                action = I18n.translate(I18nKeys.Action.CLOSE),
                shortcut = formatKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutMask)),
                description = I18n.translate(I18nKeys.Shortcut.CLOSE_TAB)
            ),
            ShortcutItem(
                action = I18n.translate(I18nKeys.Action.FORMAT_FILE),
                shortcut = formatFormatKeyStroke(),
                description = I18n.translate(I18nKeys.Shortcut.FORMAT_FILE)
            ),
            ShortcutItem(
                action = I18n.translate(I18nKeys.Toolbar.SETTINGS),
                shortcut = formatKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, shortcutMask)),
                description = I18n.translate(I18nKeys.Shortcut.OPEN_SETTINGS)
            ),
        )
    }
    
    private val tableModel = object : AbstractTableModel() {
        private fun getShortcuts(): List<ShortcutItem> {
            return this@KeymapPanel.getShortcuts()
        }
        
        override fun getRowCount(): Int = getShortcuts().size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(column: Int): String = when (column) {
            0 -> if (isEnglish()) "Action" else "功能"
            1 -> I18n.translate(I18nKeys.Keymap.SHORTCUT)
            else -> if (isEnglish()) "Description" else "说明"
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val shortcuts = getShortcuts()
            val item = shortcuts[rowIndex]
            return when (columnIndex) {
                0 -> item.action
                1 -> item.shortcut
                else -> item.description
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
        // 重新获取快捷键列表（因为语言可能已改变）
        // 直接触发表格数据更新
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

    /**
     * 格式化格式化快捷键（Option+Command+L 或 Alt+Ctrl+L）
     */
    private fun formatFormatKeyStroke(): String {
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
