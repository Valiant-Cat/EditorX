package editorx.gui.theme

import editorx.core.util.IconLoader
import editorx.gui.workbench.editor.TextArea
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.SwingUtilities
import javax.swing.UIManager

object ThemeManager {
    var currentTheme: Theme = Theme.Light
        set(value) {
            field = value
            applyTheme()
        }

    init {
        IconLoader.setDarkThemeProvider { currentTheme is Theme.Dark }
    }

    private var themeChangeListeners = mutableListOf<() -> Unit>()

    fun addThemeChangeListener(listener: () -> Unit) {
        themeChangeListeners.add(listener)
    }

    fun removeThemeChangeListener(listener: () -> Unit) {
        themeChangeListeners.remove(listener)
    }

    private fun applyTheme() {
        // 更新 UIManager 中的主题颜色
        installToSwing()
        // 通知所有监听器（这是主要的更新机制，组件需要知道如何更新自己的颜色）
        themeChangeListeners.forEach {
            try {
                it()
            } catch (e: Exception) {
                // 忽略单个监听器的错误，确保其他监听器仍能执行
                org.slf4j.LoggerFactory.getLogger("ThemeManager").warn("主题变更监听器执行失败", e)
            }
        }
        // 遍历所有打开的窗口并触发重绘（作为补充，确保所有窗口都能响应）
        // 注意：这主要是触发重绘，实际颜色更新仍依赖于监听器
        updateAllWindows()
    }

    /**
     * 更新所有打开的窗口和对话框，触发它们重绘以应用新主题
     *
     * 注意：这个方法会触发所有窗口的重绘，但组件颜色的实际更新
     * 仍然依赖于各个组件注册的 ThemeChangeListener。
     * 这里主要是确保所有窗口都能响应主题变更。
     */
    private fun updateAllWindows() {
        SwingUtilities.invokeLater {
            val windows = java.awt.Window.getWindows()
            for (window in windows) {
                if (window.isVisible) {
                    // 递归更新组件树
                    updateComponentTree(window)
                    // 触发窗口重绘
                    window.repaint()
                }
            }
        }
    }

    /**
     * 递归更新组件树，确保所有组件都能响应主题变更
     *
     * 这个方法会：
     * 1. 尝试从 UIManager 获取颜色并应用到常见组件类型
     * 2. 对 JComponent 触发 revalidate 和 repaint
     * 3. 递归处理所有子组件
     *
     * 注意：这个方法会尝试更新常见组件的颜色，但最佳实践仍然是
     * 让各个组件注册 ThemeChangeListener 来精确控制自己的颜色更新。
     */
    private fun updateComponentTree(component: java.awt.Component) {
        val theme = currentTheme

        if (component is javax.swing.JComponent) {
            // 尝试从 UIManager 获取颜色并应用到常见组件类型
            try {
                // 更新 UI，这会触发组件重新应用 UIManager 中的颜色
                val ui = component.ui
                if (ui != null) {
                    component.updateUI()
                }

                // 对于常见组件类型，直接应用主题颜色
                // 注意：这会覆盖组件之前设置的颜色，但可以确保所有组件都能响应主题变更
                when (component) {
                    is javax.swing.JLabel -> {
                        // 标签的前景色
                        component.foreground = theme.onSurface
                    }

                    is javax.swing.JButton -> {
                        // 按钮的背景色和前景色
                        component.background = theme.surface
                        component.foreground = theme.onSurface
                        component.isOpaque = true
                    }

                    is javax.swing.JPanel -> {
                        // 面板的背景色（如果面板是不透明的）
                        if (component.isOpaque) {
                            component.background = theme.surface
                        }
                    }

                    is javax.swing.JTextField -> {
                        // 文本字段的背景色和前景色
                        component.background = theme.surface
                        component.foreground = theme.onSurface
                    }

                    is TextArea -> {
                        component.applyEditorTheme(theme)
                    }

                    is javax.swing.JTextArea -> {
                        // 文本区域的背景色和前景色
                        component.background = theme.surface
                        component.foreground = theme.onSurface
                    }

                    is javax.swing.JScrollPane -> {
                        // 滚动面板的背景色
                        component.background = theme.surface
                        // 更新视口的背景色
                        component.viewport?.background = theme.surface
                        component.viewport?.isOpaque = true
                        // 更新滚动条的背景色和滑块颜色
                        component.verticalScrollBar?.background = theme.surface
                        component.horizontalScrollBar?.background = theme.surface
                    }

                    is javax.swing.JList<*> -> {
                        // 列表的背景色和前景色
                        component.background = theme.surface
                        component.foreground = theme.onSurface
                        component.selectionBackground = theme.primaryContainer
                        component.selectionForeground = theme.onPrimaryContainer
                    }

                    is javax.swing.JTable -> {
                        // 表格的背景色和前景色
                        component.background = theme.surface
                        component.foreground = theme.onSurface
                        component.selectionBackground = theme.primaryContainer
                        component.selectionForeground = theme.onPrimaryContainer
                        component.gridColor = theme.outline
                    }

                    is javax.swing.JScrollBar -> {
                        // 滚动条的背景色和滑块颜色
                        component.background = theme.surface
                        component.foreground = theme.outline
                        // 注意：滚动条的滑块颜色主要通过 UIManager 设置
                    }

                    is javax.swing.JMenu -> {
                        component.background = theme.surface
                        component.foreground = theme.onSurface
                        component.isOpaque = true
                    }

                    is javax.swing.JMenuItem -> {
                        component.background = theme.surface
                        component.foreground = theme.onSurface
                        component.isOpaque = true
                    }

                    is javax.swing.JPopupMenu -> {
                        component.background = theme.surface
                        component.foreground = theme.onSurface
                        component.isOpaque = true
                    }

                    is javax.swing.JTabbedPane -> {
                        // Tab 面板的背景色和前景色
                        component.background = theme.surface
                        component.foreground = theme.onSurfaceVariant
                        // 更新所有 tab 的背景色和前景色
                        for (i in 0 until component.tabCount) {
                            try {
                                component.setBackgroundAt(i, theme.surface)
                                component.setForegroundAt(i, theme.onSurfaceVariant)
                            } catch (e: Exception) {
                                // 某些 tab 可能不支持设置颜色，忽略
                            }
                        }
                        // 选中 tab 的前景色通过 UIManager 设置，这里触发更新
                        component.repaint()
                    }
                }
            } catch (e: Exception) {
                // 某些组件可能不支持 updateUI 或其他操作，忽略错误
            }

            // 触发组件更新
            component.revalidate()
            component.repaint()
        }

        // 递归处理子组件
        if (component is java.awt.Container) {
            for (child in component.components) {
                updateComponentTree(child)
            }
        }
    }

    /**
     * 从主题名称加载主题
     */
    fun loadTheme(name: String): Theme {
        return when (name.lowercase()) {
            "dark" -> Theme.Dark
            "light" -> Theme.Light
            else -> Theme.Light
        }
    }

    /**
     * 获取主题名称
     */
    fun getThemeName(theme: Theme): String {
        return when (theme) {
            is Theme.Dark -> "dark"
            is Theme.Light -> "light"
        }
    }

    fun installToSwing() {
        // Base surfaces
        UIManager.put("Panel.background", currentTheme.surface)
        UIManager.put("Viewport.background", currentTheme.editorBackground)
        UIManager.put("ScrollPane.background", currentTheme.editorBackground)
        UIManager.put("ScrollBar.background", currentTheme.editorBackground)
        UIManager.put("ScrollBar.thumb", currentTheme.outline)
        UIManager.put("ScrollBar.thumbDarkShadow", currentTheme.outline)
        UIManager.put("ScrollBar.thumbHighlight", currentTheme.surfaceVariant)
        UIManager.put("ScrollBar.thumbShadow", currentTheme.surfaceVariant)
        UIManager.put("TabbedPane.background", currentTheme.surface)
        UIManager.put("TabbedPane.contentAreaColor", currentTheme.surface)
        UIManager.put("MenuBar.background", currentTheme.surface)
        UIManager.put("PopupMenu.background", currentTheme.surface)
        UIManager.put("PopupMenu.foreground", currentTheme.onSurface)
        UIManager.put("Menu.background", currentTheme.surface)
        UIManager.put("Menu.foreground", currentTheme.onSurface)
        UIManager.put("MenuItem.background", currentTheme.surface)
        UIManager.put("MenuItem.foreground", currentTheme.onSurface)
        UIManager.put("MenuItem.selectionBackground", currentTheme.primaryContainer)
        UIManager.put("MenuItem.selectionForeground", currentTheme.onPrimaryContainer)
        UIManager.put("CheckBoxMenuItem.background", currentTheme.surface)
        UIManager.put("CheckBoxMenuItem.foreground", currentTheme.onSurface)
        UIManager.put("CheckBoxMenuItem.selectionBackground", currentTheme.primaryContainer)
        UIManager.put("CheckBoxMenuItem.selectionForeground", currentTheme.onPrimaryContainer)
        UIManager.put("RadioButtonMenuItem.background", currentTheme.surface)
        UIManager.put("RadioButtonMenuItem.foreground", currentTheme.onSurface)
        UIManager.put("RadioButtonMenuItem.selectionBackground", currentTheme.primaryContainer)
        UIManager.put("RadioButtonMenuItem.selectionForeground", currentTheme.onPrimaryContainer)
        UIManager.put("control", currentTheme.surface)

        // Foregrounds
        UIManager.put("Label.foreground", currentTheme.onSurface)
        UIManager.put("TabbedPane.foreground", currentTheme.onSurfaceVariant)
        UIManager.put("TabbedPane.selectedForeground", currentTheme.onSurface)

        // Outlines and dividers
        UIManager.put("Component.borderColor", currentTheme.outline)
        UIManager.put("Separator.foreground", currentTheme.outline)

        // Tree selection colors
        val treeSelectionBg = java.awt.Color(
            currentTheme.onSurface.red,
            currentTheme.onSurface.green,
            currentTheme.onSurface.blue,
            0x20
        )
        val treeSelectionInactiveBg = java.awt.Color(
            currentTheme.onSurface.red,
            currentTheme.onSurface.green,
            currentTheme.onSurface.blue,
            0x14
        )
        UIManager.put("Tree.selectionBackground", treeSelectionBg)
        UIManager.put("Tree.selectionInactiveBackground", treeSelectionInactiveBg)
        UIManager.put("Tree.textBackground", currentTheme.surface)
        UIManager.put("Tree.selectionForeground", currentTheme.onSurface)
        UIManager.put("Tree.selectionInactiveForeground", currentTheme.onSurface)
        UIManager.put("Tree.textForeground", currentTheme.onSurface)

        // Rounding for Material feel (FlatLaf honors these keys)
        UIManager.put("Component.arc", 12)
        UIManager.put("Button.arc", 14)
        UIManager.put("TextComponent.arc", 10)

        // SplitPane / Divider: remove extra borders/lines
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder())
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder())
        // Thin, neutral divider grip
        UIManager.put("SplitPaneDivider.gripColor", Color(0xC8, 0xC8, 0xC8))
        UIManager.put("SplitPaneDivider.background", currentTheme.surface)

        // Dialog and OptionPane
        UIManager.put("Dialog.background", currentTheme.surface)
        UIManager.put("OptionPane.background", currentTheme.surface)
        UIManager.put("OptionPane.messageForeground", currentTheme.onSurface)
        UIManager.put("OptionPane.foreground", currentTheme.onSurface)
        UIManager.put("Button.background", currentTheme.surface)
        UIManager.put("Button.foreground", currentTheme.onSurface)
        UIManager.put("TextField.background", currentTheme.surface)
        UIManager.put("TextField.foreground", currentTheme.onSurface)
        UIManager.put("TextArea.background", currentTheme.surface)
        UIManager.put("TextArea.foreground", currentTheme.onSurface)
    }

    // Design tokens centralized here
    val separator: Color get() = Color(0xDE, 0xDE, 0xDE)
    val activityBarBackground: Color get() = Color(0xF2, 0xF2, 0xF2)
    val activityBarItemSelectedBackground: Color get() = Color(62, 115, 185, 0x88)
    val activityBarItemHoverBackground: Color get() = Color(0, 0, 0, 0x20) // very light overlay

    // Editor tabs
    val editorTabSelectedUnderline: Color
        get() = if (currentTheme is Theme.Dark) Color(0x5B, 0x5D, 0x62) else currentTheme.primary
    val editorTabSelectedForeground: Color get() = currentTheme.onSurface
    val editorTabForeground: Color get() = currentTheme.onSurfaceVariant
    val editorTabHoverBackground: Color get() = Color(0, 0, 0, 15)
    val editorTabCloseDefault: Color get() = Color(0x8A, 0x8A, 0x8A)
    val editorTabCloseSelected: Color get() = currentTheme.onSurface

    // 更淡的半透明浅灰（偏白），用于关闭按钮悬停背景（≈8%）
    val editorTabCloseHoverBackground: Color get() = Color(255, 255, 255, 20)
    val editorTabCloseInvisible: Color get() = Color(0, 0, 0, 0)
}
