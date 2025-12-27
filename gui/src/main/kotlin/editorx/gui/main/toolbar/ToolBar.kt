package editorx.gui.main.toolbar

import editorx.gui.theme.ThemeManager
import editorx.gui.main.MainWindow
import org.slf4j.LoggerFactory
import java.awt.Insets
import javax.swing.*

/**
 * 顶部工具栏：常用操作的快捷入口。
 */
class ToolBar(private val mainWindow: MainWindow) : JPanel() {
    companion object {
        private val logger = LoggerFactory.getLogger(ToolBar::class.java)
        private const val ICON_SIZE = 14
    }

    // 按插件 ID 分组存储按钮
    private val itemsByPlugin = mutableMapOf<String, MutableMap<String, JButton>>()
    private val itemOrder = mutableListOf<String>() // 保持添加顺序

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = true
        // 初始时没有 item，隐藏 ToolBar
        isVisible = false
        updateTheme()
        
        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }
    }
    
    private fun updateTheme() {
        background = ThemeManager.currentTheme.toolbarBackground
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.currentTheme.statusBarSeparator),
            BorderFactory.createEmptyBorder(4, 8, 4, 8),
        )
        revalidate()
        repaint()
    }

    /**
     * 添加 ToolBar 按钮
     * @param pluginId 插件 ID
     * @param id 按钮的唯一标识符
     * @param icon 按钮图标
     * @param text 按钮文本
     * @param action 按钮点击时的动作
     */
    fun addItem(pluginId: String, id: String, icon: Icon?, text: String, action: () -> Unit) {
        // 如果已存在相同 ID 的按钮，先移除
        if (itemOrder.contains(id)) {
            removeItem(pluginId, id)
        }

        val button = buildToolBarItem(icon, text, action)
        itemsByPlugin.getOrPut(pluginId) { mutableMapOf() }[id] = button
        itemOrder.add(id)

        // 重新构建 UI
        rebuildUI()
    }

    /**
     * 移除指定插件的所有按钮
     * @param pluginId 插件 ID
     */
    fun removeItems(pluginId: String) {
        val items = itemsByPlugin.remove(pluginId) ?: return
        itemOrder.removeAll(items.keys)
        rebuildUI()
    }

    /**
     * 移除指定按钮
     * @param pluginId 插件 ID
     * @param id 按钮 ID
     */
    private fun removeItem(pluginId: String, id: String) {
        val pluginItems = itemsByPlugin[pluginId] ?: return
        pluginItems.remove(id)?.let {
            itemOrder.remove(id)
        }
        if (pluginItems.isEmpty()) {
            itemsByPlugin.remove(pluginId)
        }
    }

    /**
     * 设置按钮的启用/禁用状态
     * @param id 按钮的唯一标识符
     * @param enabled 是否启用
     */
    fun setItemEnabled(id: String, enabled: Boolean) {
        itemsByPlugin.values.forEach { pluginItems ->
            pluginItems[id]?.isEnabled = enabled
        }
    }

    /**
     * 重新构建 UI
     */
    private fun rebuildUI() {
        // 移除所有组件
        removeAll()

        // 按顺序添加所有按钮
        val allItems = itemOrder.mapNotNull { itemId ->
            itemsByPlugin.values.firstOrNull { it.containsKey(itemId) }?.get(itemId)
        }

        // 根据是否有 item 来决定是否显示 ToolBar
        if (allItems.isEmpty()) {
            isVisible = false
        } else {
            isVisible = true
            allItems.forEachIndexed { index, button ->
                add(button)
                // 在按钮之间添加分隔符
                if (index < allItems.size - 1) {
                    add(Box.createHorizontalStrut(6))
                }
            }
            // 添加尾部分隔符
            add(Box.createHorizontalStrut(12))
        }

        revalidate()
        repaint()
    }

    /**
     * 创建带图标和标签的按钮（icon + label 格式）
     */
    private fun buildToolBarItem(icon: Icon?, text: String, action: () -> Unit): JButton {
        return JButton(text, icon).apply {
            toolTipText = text
            isFocusable = false
            // 设置字体大小
            font = font.deriveFont(11f)
            // 设置排列位置
            horizontalAlignment = SwingConstants.RIGHT
            verticalAlignment = SwingConstants.CENTER
            // 设置图标和文本之间的间距
            iconTextGap = 4
            // 设置按钮边距
            margin = Insets(4, 8, 4, 8)
            addActionListener { action() }
        }
    }
}



