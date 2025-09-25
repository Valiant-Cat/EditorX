package editorx.gui.theme

import java.awt.Color
import javax.swing.UIManager
import javax.swing.BorderFactory

data class Material3Palette(
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
)

object Material3 {
    // Baseline Material 3 light scheme (static). Values from M3 guidelines.
    val Light = Material3Palette(
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
    )
}

object ThemeManager {
    var palette: Material3Palette = Material3.Light

    fun installToUI() {
        val p = palette
        // Base surfaces
        UIManager.put("Panel.background", p.surface)
        UIManager.put("Viewport.background", p.surface)
        UIManager.put("ScrollPane.background", p.surface)
        UIManager.put("TabbedPane.background", p.surface)
        UIManager.put("TabbedPane.contentAreaColor", p.surface)
        UIManager.put("MenuBar.background", p.surface)
        UIManager.put("PopupMenu.background", p.surface)
        UIManager.put("control", p.surface)

        // Foregrounds
        UIManager.put("Label.foreground", p.onSurface)
        UIManager.put("TabbedPane.foreground", p.onSurfaceVariant)
        UIManager.put("TabbedPane.selectedForeground", p.onSurface)

        // Outlines and dividers
        UIManager.put("Component.borderColor", p.outline)
        UIManager.put("Separator.foreground", p.outline)

        // Rounding for Material feel (FlatLaf honors these keys)
        UIManager.put("Component.arc", 12)
        UIManager.put("Button.arc", 14)
        UIManager.put("TextComponent.arc", 10)

        // SplitPane / Divider: remove extra borders/lines
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder())
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder())
        // Thin, neutral divider grip
        UIManager.put("SplitPaneDivider.gripColor", Color(0xC8, 0xC8, 0xC8))
        UIManager.put("SplitPaneDivider.background", p.surface)
    }

    // Design tokens centralized here
    val separator: Color get() = Color(0xDE, 0xDE, 0xDE)
    val activityBarBackground: Color get() = Color(0xF2, 0xF2, 0xF2)
    val activityBarItemSelected: Color get() = palette.primaryContainer
    val activityBarItemHover: Color get() = Color(0, 0, 0, 20) // very light overlay

    // Editor tabs
    val editorTabSelectedUnderline: Color get() = palette.primary
    val editorTabSelectedForeground: Color get() = palette.onSurface
    val editorTabForeground: Color get() = palette.onSurfaceVariant
    val editorTabHoverBackground: Color get() = Color(0, 0, 0, 15)
    val editorTabCloseDefault: Color get() = Color(0x8A, 0x8A, 0x8A)
    val editorTabCloseSelected: Color get() = palette.onSurface
    // 更淡的半透明浅灰（偏白），用于关闭按钮悬停背景（≈8%）
    val editorTabCloseHoverBackground: Color get() = Color(255, 255, 255, 20)
    val editorTabCloseInvisible: Color get() = Color(0, 0, 0, 0)
}
