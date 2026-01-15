package editorx.gui.workbench.editor

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.gui.theme.ThemeManager
import editorx.gui.shortcut.ShortcutIds
import editorx.gui.shortcut.ShortcutManager
import editorx.gui.MainWindow
import java.awt.*
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 当编辑器没有打开任何标签时显示的视图，展示常用快捷键
 */
class EmptyEditorView(private val mainWindow: MainWindow) : JPanel() {
    
    private data class ShortcutItem(
        val name: String,
        val keyStroke: javax.swing.KeyStroke?,
        val nameKey: String,
        val descriptionKey: String? = null
    )
    
    init {
        layout = BorderLayout()
        isOpaque = false
        updateTheme()
        
        ThemeManager.addThemeChangeListener { updateTheme() }
        
        refreshContent()
    }
    
    private fun updateTheme() {
        // 保持透明背景
        repaint()
    }
    
    fun refreshContent() {
        removeAll()
        
        val centerPanel = createCenterPanel()
        add(centerPanel, BorderLayout.CENTER)
        
        revalidate()
        repaint()
    }
    
    private fun createCenterPanel(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = EmptyBorder(40, 0, 40, 0)
        }
        
        // 顶部弹性空间
        val topSpacer = JPanel().apply {
            isOpaque = false
            preferredSize = Dimension(0, 0)
            minimumSize = Dimension(0, 0)
        }
        val topGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        }
        panel.add(topSpacer, topGbc)
        
        // 快捷键列表
        val shortcutsPanel = createShortcutsPanel()
        val shortcutsGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 1
            weightx = 1.0
            weighty = 0.0
            anchor = GridBagConstraints.CENTER
        }
        panel.add(shortcutsPanel, shortcutsGbc)
        
        // 底部弹性空间
        val bottomSpacer = JPanel().apply {
            isOpaque = false
            preferredSize = Dimension(0, 0)
            minimumSize = Dimension(0, 0)
        }
        val bottomGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 2
            weightx = 1.0
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        }
        panel.add(bottomSpacer, bottomGbc)
        
        return panel
    }
    
    private fun createShortcutsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = CENTER_ALIGNMENT
        }
        
        val shortcuts = getCommonShortcuts()
        shortcuts.forEach { item ->
            val shortcutRow = createShortcutRow(item)
            panel.add(shortcutRow)
            panel.add(Box.createVerticalStrut(8))
        }
        
        return panel
    }
    
    private fun createShortcutRow(item: ShortcutItem): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            preferredSize = Dimension(300, 32)
            maximumSize = Dimension(300, 32)
            border = javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0)
        }
        
        // 操作名称
        val nameLabel = JLabel(item.name).apply {
            font = font.deriveFont(Font.PLAIN, 14f)
            foreground = ThemeManager.currentTheme.onSurface
            horizontalAlignment = SwingConstants.LEFT
        }
        panel.add(nameLabel, BorderLayout.WEST)
        
        // 快捷键显示
        val shortcutPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
        }
        
        // 特殊处理：如果没有快捷键（拖放提示）
        if (item.keyStroke == null && item.nameKey.isEmpty()) {
            // 不显示任何快捷键
        } else if (item.keyStroke == null) {
            shortcutPanel.add(KeyBadge("⇧"))
            shortcutPanel.add(KeyBadge("⇧"))
        } else {
            val badges = createKeyBadges(item.keyStroke)
            badges.forEach { badge ->
                shortcutPanel.add(badge)
            }
        }
        
        panel.add(shortcutPanel, BorderLayout.EAST)
        
        return panel
    }
    
    private fun getCommonShortcuts(): List<ShortcutItem> {
        val shortcuts = mutableListOf<ShortcutItem>()
        val shortcutMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        
        // 从 ShortcutRegistry 获取已注册的快捷键
        val registeredShortcuts = ShortcutManager.getAllShortcuts()
        
        val isZh = I18n.locale().language == "zh"
        
        // 全局搜索 - Command+K
        val searchBinding = registeredShortcuts.find { it.id == ShortcutIds.Global.SEARCH }
        val searchName = if (isZh) "全局搜索" else "Global Search"
        shortcuts.add(
            ShortcutItem(
                name = searchName,
                keyStroke = searchBinding?.keyStroke ?: javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_K, shortcutMask),
                nameKey = searchBinding?.nameKey ?: I18nKeys.Action.GLOBAL_SEARCH,
                descriptionKey = searchBinding?.descriptionKey
            )
        )
        
        // Command+N - 新建文件
        val newFileBinding = registeredShortcuts.find { it.id == ShortcutIds.Editor.NEW_FILE }
        if (newFileBinding != null) {
            shortcuts.add(
                ShortcutItem(
                    name = newFileBinding.displayName,
                    keyStroke = newFileBinding.keyStroke,
                    nameKey = newFileBinding.nameKey,
                    descriptionKey = newFileBinding.descriptionKey
                )
            )
        } else {
            shortcuts.add(
                ShortcutItem(
                    name = I18n.translate(I18nKeys.Action.NEW_FILE),
                    keyStroke = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask),
                    nameKey = I18nKeys.Action.NEW_FILE
                )
            )
        }
        
        // Command+, - 打开设置
        val settingsBinding = registeredShortcuts.find { it.id == ShortcutIds.Global.SETTINGS }
        if (settingsBinding != null) {
            shortcuts.add(
                ShortcutItem(
                    name = settingsBinding.displayName,
                    keyStroke = settingsBinding.keyStroke,
                    nameKey = settingsBinding.nameKey,
                    descriptionKey = settingsBinding.descriptionKey
                )
            )
        } else {
            shortcuts.add(
                ShortcutItem(
                    name = I18n.translate(I18nKeys.Toolbar.SETTINGS),
                    keyStroke = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, shortcutMask),
                    nameKey = I18nKeys.Toolbar.SETTINGS
                )
            )
        }
        
        // 将文件拖放到此处以打开 - 没有快捷键
        val dragDropName = if (isZh) "将文件拖放到此处以打开" else "Drag and drop files here to open"
        shortcuts.add(
            ShortcutItem(
                name = dragDropName,
                keyStroke = null,
                nameKey = "" // 特殊项，不需要 i18n key
            )
        )
        
        return shortcuts
    }
    
    private fun getDefaultShortcuts(shortcutMask: Int): List<ShortcutItem> {
        return listOf(
            ShortcutItem(
                name = I18n.translate(I18nKeys.Action.GLOBAL_SEARCH),
                keyStroke = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_K, shortcutMask),
                nameKey = I18nKeys.Action.GLOBAL_SEARCH
            ),
            ShortcutItem(
                name = I18n.translate(I18nKeys.Action.NEW_FILE),
                keyStroke = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask),
                nameKey = I18nKeys.Action.NEW_FILE
            ),
            ShortcutItem(
                name = I18n.translate(I18nKeys.Toolbar.SETTINGS),
                keyStroke = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, shortcutMask),
                nameKey = I18nKeys.Toolbar.SETTINGS
            ),
            ShortcutItem(
                name = I18n.translate(I18nKeys.Action.CLOSE),
                keyStroke = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutMask),
                nameKey = I18nKeys.Action.CLOSE
            )
        )
    }
}
