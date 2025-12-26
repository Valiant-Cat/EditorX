package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.PluginManager
import editorx.core.gui.GuiContext
import editorx.gui.main.MainWindow
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.InputMap
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent

class SettingsDialog(
    owner: MainWindow,
    private val environment: GuiContext,
    private val pluginManager: PluginManager,
    private val defaultSection: Section = Section.APPEARANCE,
) : JDialog(owner, I18n.translate(I18nKeys.Settings.TITLE), true) {

    enum class Section { APPEARANCE, KEYMAP, PLUGINS, CACHE }

    private data class SectionItem(
        val section: Section,
        val key: String,
    ) {
        fun label(): String = I18n.translate(key)
    }

    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout).apply { isOpaque = false }

    private val appearancePanel = AppearancePanel(environment.settings)
    private val keymapPanel = KeymapPanel()
    private val pluginsPanel = PluginsPanel(pluginManager, environment.settings)
    private val cachePanel = CachePanel(environment)

    private val listModel = DefaultListModel<SectionItem>().apply {
        addElement(SectionItem(Section.APPEARANCE, I18nKeys.Settings.APPEARANCE))
        addElement(SectionItem(Section.KEYMAP, I18nKeys.Settings.KEYMAP))
        addElement(SectionItem(Section.PLUGINS, I18nKeys.Settings.PLUGINS))
        addElement(SectionItem(Section.CACHE, I18nKeys.Settings.CACHE))
    }

    private val navigation = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = 28
        border = BorderFactory.createEmptyBorder()
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val item = value as? SectionItem
                c.text = item?.label() ?: ""
                c.border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
                
                // 检查当前面板是否有待保存的更改
                val currentPanel = getPanelForSection(item?.section)
                val hasChanges = currentPanel is SettingsPanel && currentPanel.hasPendingChanges()
                
                // 设置选中项的颜色
                if (isSelected) {
                    c.background = Color(0x3D, 0x8E, 0xF6) // 蓝色背景
                    c.foreground = Color.WHITE // 白色文字
                } else {
                    c.background = Color.WHITE
                    // 如果有待保存的更改，文字显示为蓝色
                    c.foreground = if (hasChanges) Color(0x3D, 0x8E, 0xF6) else Color.BLACK
                }
                c.isOpaque = true
                
                return c
            }
        }
        addListSelectionListener {
            val item = selectedValue ?: return@addListSelectionListener
            showSection(item.section)
        }
    }
    
    /**
     * 根据 Section 获取对应的面板
     */
    private fun getPanelForSection(section: Section?): JPanel? {
        return when (section) {
            Section.APPEARANCE -> appearancePanel
            Section.KEYMAP -> keymapPanel
            Section.PLUGINS -> pluginsPanel
            Section.CACHE -> cachePanel
            null -> null
        }
    }
    
    /**
     * 当面板的更改状态更新时调用（由 SettingsPanel 调用）
     */
    fun onPanelChangesUpdated() {
        navigation.repaint()
    }

    private val i18nListener = {
        SwingUtilities.invokeLater {
            title = I18n.translate(I18nKeys.Settings.TITLE)
            navigation.repaint()
            appearancePanel.refresh()
            keymapPanel.refresh()
            cachePanel.refresh()
        }
    }

    init {
        contentPanel.add(appearancePanel, Section.APPEARANCE.name)
        contentPanel.add(keymapPanel, Section.KEYMAP.name)
        contentPanel.add(pluginsPanel, Section.PLUGINS.name)
        contentPanel.add(cachePanel, Section.CACHE.name)

        layout = BorderLayout()
        add(buildBody(), BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)

        size = Dimension(940, 640)
        setLocationRelativeTo(owner)
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        navigation.selectedIndex = listModel.elements().asSequence().indexOfFirst { it.section == defaultSection }
            .takeIf { it >= 0 } ?: 0

        // 注册 ESC 键关闭对话框
        setupEscKeyBinding()

        I18n.addListener(i18nListener)
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                pluginsPanel.disposePanel()
                I18n.removeListener(i18nListener)
            }
        })
    }
    
    private fun setupEscKeyBinding() {
        val escKey = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
        val inputMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val actionMap = rootPane.actionMap
        
        inputMap.put(escKey, "closeDialog")
        actionMap.put("closeDialog", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                dispose()
            }
        })
    }

    private fun buildBody(): JComponent {
        val navigationPane = JPanel(BorderLayout()).apply {
            background = Color.WHITE
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Color(0xD0, 0xD0, 0xD0)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
            )
            val title = JLabel(I18n.translate(I18nKeys.Settings.PREFERENCES)).apply {
                font = font.deriveFont(Font.BOLD, 13f)
                border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
            }
            val scroll = JScrollPane(navigation).apply {
                border = BorderFactory.createMatteBorder(1, 1, 1, 1, Color(0xDD, 0xDD, 0xDD))
                background = Color.WHITE
                viewport.background = Color.WHITE
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }
            add(title, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }

        val contentWrapper = JPanel(BorderLayout()).apply {
            background = Color(0xF3, 0xF4, 0xF6)
            border = BorderFactory.createEmptyBorder(16, 16, 16, 24)
            val inner = JPanel(BorderLayout()).apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 1, 1, Color(0xD0, 0xD0, 0xD0)),
                    BorderFactory.createEmptyBorder(24, 28, 28, 28)
                )
                background = Color.WHITE
                add(contentPanel, BorderLayout.CENTER)
            }
            add(inner, BorderLayout.CENTER)
        }

        val mainSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationPane, contentWrapper).apply {
            setDividerLocation(240)
            setResizeWeight(0.0)
            isOneTouchExpandable = false
            setContinuousLayout(true)
            border = BorderFactory.createEmptyBorder()
        }
        
        // 在 SettingsDialog 的 JSplitPane divider 上添加鼠标监听器
        // 检查鼠标是否在 PluginsPanel 区域内，如果是则阻止拖拽
        val mainDivider = (mainSplitPane.ui as? javax.swing.plaf.basic.BasicSplitPaneUI)?.divider
        mainDivider?.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                // 检查鼠标是否在 PluginsPanel 的 JSplitPane divider 区域内
                if (isMouseOverPluginsPanelDivider(e)) {
                    e.consume()
                }
            }
        })
        
        mainDivider?.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                // 如果正在拖拽 PluginsPanel 的 JSplitPane，则阻止 SettingsDialog 的 JSplitPane 拖拽
                if (isMouseOverPluginsPanelDivider(e)) {
                    e.consume()
                }
            }
        })
        
        return mainSplitPane
    }

    private fun buildFooter(): JComponent {
        val resetPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6)).apply {
            isOpaque = false
            add(JButton(I18n.translate(I18nKeys.Action.RESET)).apply {
                isFocusable = false
                addActionListener { onResetPressed() }
            })
        }
        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 12, 6)).apply {
            isOpaque = false
            add(JButton(I18n.translate(I18nKeys.Action.CANCEL)).apply {
                addActionListener { dispose() }
            })
            add(JButton(I18n.translate(I18nKeys.Action.CONFIRM)).apply {
                addActionListener {
                    // 应用所有面板的更改
                    var needRestart = false
                    
                    // 应用外观设置的更改
                    needRestart = appearancePanel.applyChanges() || needRestart
                    
                    // 应用其他面板的更改（如果它们继承 SettingsPanel）
                    (keymapPanel as? SettingsPanel)?.apply { needRestart = applyChanges() || needRestart }
                    (pluginsPanel as? SettingsPanel)?.apply { needRestart = applyChanges() || needRestart }
                    (cachePanel as? SettingsPanel)?.apply { needRestart = applyChanges() || needRestart }
                    
                    // 同步所有设置
                    environment.settings.sync()
                    
                    // 如果语言改变需要重启，显示提示对话框
                    if (needRestart && appearancePanel.showRestartDialog()) {
                        // 用户选择重启，退出应用
                        System.exit(0)
                    } else {
                        dispose()
                    }
                }
            })
        }
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color(0xD0, 0xD0, 0xD0)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
            )
            isOpaque = false
            add(resetPanel, BorderLayout.WEST)
            add(actionPanel, BorderLayout.EAST)
        }
    }

    private fun showSection(section: Section) {
        cardLayout.show(contentPanel, section.name)
        when (section) {
            Section.APPEARANCE -> appearancePanel.refresh()
            Section.KEYMAP -> keymapPanel.refresh()
            Section.PLUGINS -> pluginsPanel.refreshView()
            Section.CACHE -> cachePanel.refresh()
        }
    }

    private fun onResetPressed() {
        // 仅恢复语言为默认简体中文，其他设置保留。后续可扩展更多重置选项。
        appearancePanel.resetToDefault()
        navigation.repaint()
        contentPanel.revalidate()
        contentPanel.repaint()
    }
    
    /**
     * 检查鼠标是否在 PluginsPanel 的 JSplitPane divider 上
     */
    private fun isMouseOverPluginsPanelDivider(e: java.awt.event.MouseEvent): Boolean {
        // 检查当前显示的面板是否是 PluginsPanel
        // 通过检查 CardLayout 当前显示的组件来判断
        val currentComponent = contentPanel.components.firstOrNull { it.isVisible } ?: return false
        if (currentComponent != pluginsPanel) return false
        
        // 检查 PluginsPanel 内部是否有 JSplitPane
        val pluginSplitPane = findJSplitPane(pluginsPanel) ?: return false
        
        // 获取 PluginsPanel 的 JSplitPane divider
        val pluginDivider = (pluginSplitPane.ui as? javax.swing.plaf.basic.BasicSplitPaneUI)?.divider ?: return false
        
        // 将鼠标坐标转换为 PluginsPanel 的 JSplitPane divider 的坐标系统
        val point = SwingUtilities.convertPoint(e.component, e.point, pluginDivider)
        
        // 检查鼠标是否在 divider 的边界内（包括一些容差范围，因为 divider 可能很窄）
        val bounds = pluginDivider.bounds
        val tolerance = 5 // 5像素容差
        return point.x >= bounds.x - tolerance && 
               point.x <= bounds.x + bounds.width + tolerance &&
               point.y >= bounds.y - tolerance && 
               point.y <= bounds.y + bounds.height + tolerance
    }
    
    /**
     * 递归查找组件中的第一个 JSplitPane
     */
    private fun findJSplitPane(component: java.awt.Component): JSplitPane? {
        if (component is JSplitPane) {
            return component
        }
        if (component is Container) {
            for (i in 0 until component.componentCount) {
                findJSplitPane(component.getComponent(i))?.let { return it }
            }
        }
        return null
    }

}
