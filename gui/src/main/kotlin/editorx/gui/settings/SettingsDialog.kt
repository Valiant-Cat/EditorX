package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.PluginManager
import editorx.core.gui.GuiContext
import editorx.gui.main.MainWindow
import editorx.gui.core.RestartHelper
import editorx.gui.ThemeManager
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.MouseInfo
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

    companion object {
        @Volatile
        private var currentInstance: SettingsDialog? = null

        /**
         * 显示设置对话框，如果已存在则将其带到前台
         */
        fun showOrBringToFront(
            owner: MainWindow,
            environment: GuiContext,
            pluginManager: PluginManager,
            defaultSection: Section = Section.APPEARANCE
        ) {
            val existing = currentInstance
            if (existing != null && existing.isVisible) {
                existing.toFront()
                existing.requestFocus()
                return
            }

            val dialog = SettingsDialog(owner, environment, pluginManager, defaultSection)
            currentInstance = dialog

            // 监听对话框关闭事件，清除引用
            dialog.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: java.awt.event.WindowEvent?) {
                    currentInstance = null
                }
            })

            dialog.isVisible = true
        }
    }

    private data class SectionItem(
        val section: Section,
        val key: String,
    ) {
        fun label(): String = I18n.translate(key)
    }

    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout).apply { isOpaque = false }

    private val appearancePanel = AppearancePanel(environment.getSettings())
    private val keymapPanel = KeymapPanel()
    private val pluginsPanel = PluginsPanel(pluginManager, environment.getSettings())
    private val cachePanel = CachePanel(environment)
    
    // 用于限制 SettingsDialog 的 JSplitPane 最大 dividerLocation
    private var mainSplitPane: JSplitPane? = null
    private val minContentWidthForPlugins = 600 // 插件面板需要的最小宽度
    
    // 保存组件引用以便更新主题
    private var navigationPane: JPanel? = null
    private var navigationTitle: JLabel? = null
    private var navigationScroll: JScrollPane? = null
    private var contentWrapper: JPanel? = null
    private var contentInner: JPanel? = null
    private var footerPanel: JPanel? = null

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
        background = ThemeManager.currentTheme.surface
        foreground = ThemeManager.currentTheme.onSurface
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
                val theme = ThemeManager.currentTheme
                if (isSelected) {
                    c.background = theme.primary // 主题主色背景
                    c.foreground = theme.onPrimary // 主题主色上的文字
                } else {
                    c.background = theme.surface
                    // 如果有待保存的更改，文字显示为主题主色
                    c.foreground = if (hasChanges) theme.primary else theme.onSurface
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
        
        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }
        updateTheme()
        
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                pluginsPanel.disposePanel()
                I18n.removeListener(i18nListener)
            }
        })
    }
    
    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        background = theme.surface
        
        // 更新导航面板
        navigationPane?.let { pane ->
            pane.background = theme.surface
            pane.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, theme.outline),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
            )
        }
        
        // 更新导航标题
        navigationTitle?.foreground = theme.onSurface
        
        // 更新导航滚动 pane
        navigationScroll?.let { scroll ->
            scroll.border = BorderFactory.createMatteBorder(1, 1, 1, 1, theme.outline)
            scroll.background = theme.surface
            scroll.viewport.background = theme.surface
            scroll.viewport.isOpaque = true
        }
        
        // 更新导航列表背景色
        navigation.background = theme.surface
        navigation.foreground = theme.onSurface
        
        // 更新内容包装器
        contentWrapper?.background = theme.surfaceVariant
        
        // 更新内容内层
        contentInner?.let { inner ->
            inner.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, theme.outline),
                BorderFactory.createEmptyBorder(24, 28, 28, 28)
            )
            inner.background = theme.surface
        }
        
        // 更新页脚
        footerPanel?.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, theme.outline),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        )
        
        // 更新页脚按钮颜色
        footerPanel?.components?.forEach { panel ->
            if (panel is JPanel) {
                panel.components.forEach { component ->
                    if (component is JButton) {
                        component.background = theme.surface
                        component.foreground = theme.onSurface
                    }
                }
            }
        }
        
        // 更新导航列表（需要重新渲染）
        navigation.repaint()
        
        repaint()
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
        val theme = ThemeManager.currentTheme
        
        navigationPane = JPanel(BorderLayout()).apply {
            background = theme.surface
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, theme.outline),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
            )
            navigationTitle = JLabel(I18n.translate(I18nKeys.Settings.PREFERENCES)).apply {
                font = font.deriveFont(Font.BOLD, 13f)
                border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
                foreground = theme.onSurface
            }
            navigationScroll = JScrollPane(navigation).apply {
                border = BorderFactory.createMatteBorder(1, 1, 1, 1, theme.outline)
                background = theme.surface
                viewport.background = theme.surface
                viewport.isOpaque = true
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }
            add(navigationTitle, BorderLayout.NORTH)
            add(navigationScroll, BorderLayout.CENTER)
        }

        contentWrapper = JPanel(BorderLayout()).apply {
            background = theme.surfaceVariant
            border = BorderFactory.createEmptyBorder(16, 16, 16, 24)
            contentInner = JPanel(BorderLayout()).apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 1, 1, theme.outline),
                    BorderFactory.createEmptyBorder(24, 28, 28, 28)
                )
                background = theme.surface
                add(contentPanel, BorderLayout.CENTER)
            }
            add(contentInner, BorderLayout.CENTER)
        }

        val mainSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationPane, contentWrapper).apply {
            setDividerLocation(240)
            setResizeWeight(0.0)
            isOneTouchExpandable = false
            setContinuousLayout(true)
            border = BorderFactory.createEmptyBorder()
        }
        
        // 保存 mainSplitPane 的引用，以便在其他方法中使用
        this.mainSplitPane = mainSplitPane
        
        // 在 SettingsDialog 的 JSplitPane divider 上添加鼠标监听器
        // 当显示 PluginsPanel 时，如果鼠标在 PluginsPanel 的 JSplitPane divider 区域内，
        // 则完全禁用 SettingsDialog 的拖拽，避免嵌套拖拽冲突
        val mainDivider = (mainSplitPane.ui as? javax.swing.plaf.basic.BasicSplitPaneUI)?.divider
        var isDraggingPluginsDivider = false
        
        mainDivider?.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                val currentComponent = contentPanel.components.firstOrNull { it.isVisible }
                if (currentComponent == pluginsPanel) {
                    // 检查鼠标是否在 PluginsPanel 的 JSplitPane divider 区域内
                    if (isMouseOverPluginsPanelDivider(e)) {
                        // 完全阻止 SettingsDialog 的拖拽
                        e.consume()
                        isDraggingPluginsDivider = true
                        return
                    }
                }
                isDraggingPluginsDivider = false
            }
            
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (isDraggingPluginsDivider) {
                    e.consume()
                }
                isDraggingPluginsDivider = false
            }
        })
        
        mainDivider?.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                val currentComponent = contentPanel.components.firstOrNull { it.isVisible }
                if (currentComponent == pluginsPanel) {
                    // 如果正在拖拽 PluginsPanel 的 divider，或者鼠标在 PluginsPanel 的 divider 上
                    if (isDraggingPluginsDivider || isMouseOverPluginsPanelDivider(e)) {
                        e.consume()
                        isDraggingPluginsDivider = true
                        return
                    }
                }
            }
        })
        
        // 移除之前的 PropertyChangeListener，因为现在通过事件 consume 来阻止嵌套拖拽
        // 不再需要监听 dividerLocation 变化来恢复位置
        
        return mainSplitPane
    }

    private fun buildFooter(): JComponent {
        val theme = ThemeManager.currentTheme
        val resetButton = JButton(I18n.translate(I18nKeys.Action.RESET)).apply {
            isFocusable = false
            background = theme.surface
            foreground = theme.onSurface
            addActionListener { onResetPressed() }
        }
        val cancelButton = JButton(I18n.translate(I18nKeys.Action.CANCEL)).apply {
            background = theme.surface
            foreground = theme.onSurface
            addActionListener { dispose() }
        }
        val confirmButton = JButton(I18n.translate(I18nKeys.Action.CONFIRM)).apply {
            background = theme.surface
            foreground = theme.onSurface
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
                environment.getSettings().sync()
                
                // 如果语言改变需要重启，显示提示对话框
                if (needRestart && appearancePanel.showRestartDialog()) {
                    // 用户选择重启，执行重启
                    dispose()
                    editorx.gui.core.RestartHelper.restart()
                } else {
                    dispose()
                }
            }
        }
        
        val resetPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6)).apply {
            isOpaque = false
            add(resetButton)
        }
        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 12, 6)).apply {
            isOpaque = false
            add(cancelButton)
            add(confirmButton)
        }
        footerPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.outline),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
            )
            isOpaque = false
            add(resetPanel, BorderLayout.WEST)
            add(actionPanel, BorderLayout.EAST)
        }
        return footerPanel!!
    }

    private fun showSection(section: Section) {
        cardLayout.show(contentPanel, section.name)
        when (section) {
            Section.APPEARANCE -> appearancePanel.refresh()
            Section.KEYMAP -> keymapPanel.refresh()
            Section.PLUGINS -> {
                pluginsPanel.refreshView()
                // 当显示 PluginsPanel 时，限制 SettingsDialog 的 JSplitPane 最大 dividerLocation
                mainSplitPane?.let { splitPane ->
                    SwingUtilities.invokeLater {
                        val maxLocation = splitPane.width - minContentWidthForPlugins
                        if (splitPane.dividerLocation > maxLocation) {
                            splitPane.dividerLocation = maxLocation.coerceAtLeast(240)
                        }
                    }
                }
            }
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
     * 判断是否应该阻止 SettingsDialog 的 JSplitPane divider 拖拽
     * 当显示 PluginsPanel 且鼠标在 PluginsPanel 的 JSplitPane divider 区域内时返回 true
     */
    private fun shouldBlockMainDividerDrag(e: java.awt.event.MouseEvent): Boolean {
        val currentComponent = contentPanel.components.firstOrNull { it.isVisible }
        // 只有当显示 PluginsPanel 时才检查
        if (currentComponent != pluginsPanel) return false
        
        // 检查鼠标是否在 PluginsPanel 的 JSplitPane divider 区域内
        return isMouseOverPluginsPanelDivider(e)
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
