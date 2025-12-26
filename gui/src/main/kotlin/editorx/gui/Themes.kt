package editorx.gui

import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.SwingUtilities
import javax.swing.UIManager

sealed class Theme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color,
    // UI 组件颜色
    val sidebarBackground: Color,
    val editorBackground: Color,
    val toolbarBackground: Color,
    val statusBarBackground: Color,
    val statusBarForeground: Color,
    val statusBarSecondaryForeground: Color,
    val statusBarSeparator: Color,
    val statusBarHoverBackground: Color,
    val cardBackground: Color,
) {

    data object Light : Theme(
        primary = Color(0x67, 0x50, 0xA4),              // #6750A4
        onPrimary = Color(0xFF, 0xFF, 0xFF),            // #FFFFFF
        primaryContainer = Color(0xEA, 0xDD, 0xFF),     // #EADDFF
        onPrimaryContainer = Color(0x21, 0x00, 0x5E),   // #21005E
        secondary = Color(0x62, 0x5B, 0x71),            // #625B71
        onSecondary = Color(0xFF, 0xFF, 0xFF),          // #FFFFFF
        surface = Color(0xFF, 0xFF, 0xFF),              // #FFFFFF
        onSurface = Color(0x1C, 0x1B, 0x1F),            // #1C1B1F
        surfaceVariant = Color(0xE7, 0xE0, 0xEC),       // #E7E0EC
        onSurfaceVariant = Color(0x49, 0x45, 0x4F),     // #49454F
        outline = Color(0x79, 0x74, 0x7E),              // #79747E
        error = Color(0xB3, 0x26, 0x1E),                // #B3261E
        // UI 组件颜色
        sidebarBackground = Color(0xF2, 0xF2, 0xF2),   // #f2f2f2
        editorBackground = Color.WHITE,                 // #ffffff
        toolbarBackground = Color.WHITE,                 // #ffffff
        statusBarBackground = Color(0xF2, 0xF2, 0xF2),   // #f2f2f2
        statusBarForeground = Color.BLACK,
        statusBarSecondaryForeground = Color.GRAY,
        statusBarSeparator = Color(0xDE, 0xDE, 0xDE),   // #dedede
        statusBarHoverBackground = Color(200, 200, 200, 0xEF),
        cardBackground = Color(0xF2, 0xF2, 0xF2),      // #f2f2f2
    )
    
    data object Dark : Theme(
        primary = Color(0xD0, 0xBC, 0xFF),              // #D0BCFF
        onPrimary = Color(0x38, 0x1E, 0x72),            // #381E72
        primaryContainer = Color(0x4F, 0x37, 0x8B),     // #4F378B
        onPrimaryContainer = Color(0xEA, 0xDD, 0xFF),   // #EADDFF
        secondary = Color(0xCC, 0xC2, 0xDC),            // #CCC2DC
        onSecondary = Color(0x33, 0x2D, 0x41),          // #332D41
        surface = Color(0x1C, 0x1B, 0x1F),              // #1C1B1F
        onSurface = Color(0xE6, 0xE1, 0xE5),            // #E6E1E5
        surfaceVariant = Color(0x49, 0x45, 0x4F),        // #49454F
        onSurfaceVariant = Color(0xCA, 0xC4, 0xD0),     // #CAC4D0
        outline = Color(0x93, 0x8F, 0x99),              // #938F99
        error = Color(0xF2, 0xB8, 0xB5),                // #F2B8B5
        // UI 组件颜色
        sidebarBackground = Color(0x14, 0x14, 0x14),   // #141414
        editorBackground = Color(0x18, 0x18, 0x18),    // #181818
        toolbarBackground = Color(0x14, 0x14, 0x14),   // #141414
        statusBarBackground = Color(0x14, 0x14, 0x14),  // #141414
        statusBarForeground = Color(0xC9, 0xD1, 0xD9),  // #c9d1d9
        statusBarSecondaryForeground = Color(0x8B, 0x94, 0x9F), // #8b949f
        statusBarSeparator = Color(0x21, 0x27, 0x2E),   // #21272e
        statusBarHoverBackground = Color(0x21, 0x27, 0x2E, 0xEF),
        cardBackground = Color(0x2D, 0x2D, 0x2D),      // #2d2d2d
    )
}

object ThemeManager {
    var currentTheme: Theme = Theme.Light
        set(value) {
            field = value
            applyTheme()
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
        UIManager.put("control", currentTheme.surface)

        // Foregrounds
        UIManager.put("Label.foreground", currentTheme.onSurface)
        UIManager.put("TabbedPane.foreground", currentTheme.onSurfaceVariant)
        UIManager.put("TabbedPane.selectedForeground", currentTheme.onSurface)

        // Outlines and dividers
        UIManager.put("Component.borderColor", currentTheme.outline)
        UIManager.put("Separator.foreground", currentTheme.outline)

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
    val activityBarItemSelectedBackground: Color get() = Color(62, 115, 185,0x88)
    val activityBarItemHoverBackground: Color get() = Color(0, 0, 0, 0x20) // very light overlay

    // Editor tabs
    val editorTabSelectedUnderline: Color get() = currentTheme.primary
    val editorTabSelectedForeground: Color get() = currentTheme.onSurface
    val editorTabForeground: Color get() = currentTheme.onSurfaceVariant
    val editorTabHoverBackground: Color get() = Color(0, 0, 0, 15)
    val editorTabCloseDefault: Color get() = Color(0x8A, 0x8A, 0x8A)
    val editorTabCloseSelected: Color get() = currentTheme.onSurface

    // 更淡的半透明浅灰（偏白），用于关闭按钮悬停背景（≈8%）
    val editorTabCloseHoverBackground: Color get() = Color(255, 255, 255, 20)
    val editorTabCloseInvisible: Color get() = Color(0, 0, 0, 0)
}
