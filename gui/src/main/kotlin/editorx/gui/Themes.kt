package editorx.gui

import java.awt.Color
import javax.swing.BorderFactory
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
        themeChangeListeners.forEach { it() }
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
        UIManager.put("Viewport.background", currentTheme.surface)
        UIManager.put("ScrollPane.background", currentTheme.surface)
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
