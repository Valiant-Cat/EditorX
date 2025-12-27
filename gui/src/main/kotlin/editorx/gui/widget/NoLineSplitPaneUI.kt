package editorx.gui.widget

import editorx.gui.theme.ThemeManager
import java.awt.*
import javax.swing.BorderFactory
import javax.swing.UIManager
import javax.swing.JSplitPane
import javax.swing.plaf.basic.BasicSplitPaneDivider
import javax.swing.plaf.basic.BasicSplitPaneUI

/**
 * A SplitPane UI whose divider does not draw top/bottom (or left/right) lines.
 * It only paints the background and a subtle grip, eliminating extra borders.
 */
class NoLineSplitPaneUI : BasicSplitPaneUI() {
    override fun createDefaultDivider(): BasicSplitPaneDivider {
        return object : BasicSplitPaneDivider(this) {
            init { border = BorderFactory.createEmptyBorder() }

            override fun paint(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    // 背景：对于水平分割（SideBar），使用 SideBar 的背景色；其他情况使用 splitPane 的背景色
                    val backgroundColor = if (orientation() == JSplitPane.HORIZONTAL_SPLIT) {
                        ThemeManager.currentTheme.sidebarBackground
                    } else {
                        splitPane.background
                    }
                    g2.color = backgroundColor
                    g2.fillRect(0, 0, width, height)

                    // 三个圆点握柄（Material风格，细腻且低对比）
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val base = UIManager.getColor("Separator.foreground") ?: Color(0xC8, 0xC8, 0xC8)
                    val grip = Color(base.red, base.green, base.blue, 120) // 更淡
                    g2.color = grip

                    val r = 1 // 更小
                    if (orientation() == JSplitPane.VERTICAL_SPLIT) {
                        // 上下分割：水平三点
                        val cy = height / 2
                        val cx = width / 2
                        val gap = 6
                        g2.fillOval(cx - gap - r, cy - r, r * 2, r * 2)
                        g2.fillOval(cx - r, cy - r, r * 2, r * 2)
                        g2.fillOval(cx + gap - r, cy - r, r * 2, r * 2)
                    } else {
                        // 左右分割：垂直三点
                        val cx = width / 2
                        val cy = height / 2
                        val gap = 6
                        g2.fillOval(cx - r, cy - gap - r, r * 2, r * 2)
                        g2.fillOval(cx - r, cy - r, r * 2, r * 2)
                        g2.fillOval(cx - r, cy + gap - r, r * 2, r * 2)
                    }
                } finally {
                    g2.dispose()
                }
            }
        }
    }

    override fun installDefaults() {
        super.installDefaults()
        splitPane.border = BorderFactory.createEmptyBorder()
    }

    private fun orientation(): Int = splitPane?.orientation ?: JSplitPane.VERTICAL_SPLIT
}
