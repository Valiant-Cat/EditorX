package editorx.gui.theme

import java.awt.Color

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
        statusBarSeparator = Color(0x23, 0x23, 0x23),   // #232323
        statusBarHoverBackground = Color(0x21, 0x27, 0x2E, 0xEF),
        cardBackground = Color(0x2D, 0x2D, 0x2D),      // #2d2d2d
    )
}