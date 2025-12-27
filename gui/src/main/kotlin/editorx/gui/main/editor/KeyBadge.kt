package editorx.gui.main.editor

import editorx.gui.theme.Theme
import editorx.gui.theme.ThemeManager
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JLabel
import javax.swing.SwingConstants

/**
 * 渲染单个键盘按键的组件
 */
class KeyBadge(private val keyText: String) : JLabel() {
    init {
        text = keyText
        horizontalAlignment = SwingConstants.CENTER
        font = font.deriveFont(Font.PLAIN, 11f)
        isOpaque = false // 使用自定义绘制
        border = javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8)
        updateTheme()
        
        ThemeManager.addThemeChangeListener { updateTheme() }
    }
    
    private var bgColor: Color = Color.GRAY
    private var fgColor: Color = Color.WHITE
    
    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        // 使用更深的背景色以匹配截图中的样式
        // 在深色主题下使用更浅的背景，在浅色主题下使用更深的背景
        bgColor = if (theme is Theme.Dark) {
            Color(theme.surfaceVariant.rgb)
        } else {
            Color(theme.surfaceVariant.rgb)
        }
        fgColor = theme.onSurfaceVariant
        repaint()
    }
    
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            // 绘制圆角矩形背景
            val shape = RoundRectangle2D.Float(
                0f, 0f, 
                width.toFloat(), height.toFloat(), 
                4f, 4f
            )
            g2.color = bgColor
            g2.fill(shape)
            
            // 绘制文字
            g2.color = fgColor
            val fm = g2.fontMetrics
            val textWidth = fm.stringWidth(text)
            val textHeight = fm.height
            val x = (width - textWidth) / 2
            val y = (height + textHeight) / 2 - fm.descent
            g2.drawString(text, x, y)
        } finally {
            g2.dispose()
        }
    }
    
    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        val textWidth = fm.stringWidth(text)
        return Dimension((textWidth + 16).coerceAtLeast(24), 24)
    }
    
    override fun getMinimumSize(): Dimension = preferredSize
    override fun getMaximumSize(): Dimension = preferredSize
}

/**
 * 从 KeyStroke 创建按键徽章列表
 */
fun createKeyBadges(keyStroke: javax.swing.KeyStroke): List<KeyBadge> {
    val badges = mutableListOf<KeyBadge>()
    val modifiers = keyStroke.modifiers
    val keyCode = keyStroke.keyCode
    
    // 处理双击 Shift 的特殊情况
    if (keyCode == KeyEvent.VK_SHIFT && modifiers == 0) {
        badges.add(KeyBadge("⇧"))
        badges.add(KeyBadge("⇧"))
        return badges
    }
    
    // 添加修饰键
    if ((modifiers and InputEvent.SHIFT_DOWN_MASK) != 0 || 
        (modifiers and InputEvent.SHIFT_MASK) != 0) {
        badges.add(KeyBadge("⇧"))
    }
    if ((modifiers and InputEvent.META_DOWN_MASK) != 0 || 
        (modifiers and InputEvent.META_MASK) != 0) {
        // macOS 使用 ⌘ 符号
        badges.add(KeyBadge("⌘"))
    } else if ((modifiers and InputEvent.CTRL_DOWN_MASK) != 0 || 
               (modifiers and InputEvent.CTRL_MASK) != 0) {
        badges.add(KeyBadge("⌃"))
    }
    if ((modifiers and InputEvent.ALT_DOWN_MASK) != 0 || 
        (modifiers and InputEvent.ALT_MASK) != 0) {
        badges.add(KeyBadge("⌥"))
    }
    
    // 添加主键
    val keyText = when (keyCode) {
        KeyEvent.VK_COMMA -> ","
        KeyEvent.VK_PERIOD -> "."
        KeyEvent.VK_SLASH -> "/"
        KeyEvent.VK_BACK_SLASH -> "\\"
        KeyEvent.VK_OPEN_BRACKET -> "["
        KeyEvent.VK_CLOSE_BRACKET -> "]"
        KeyEvent.VK_SEMICOLON -> ";"
        KeyEvent.VK_EQUALS -> "="
        KeyEvent.VK_MINUS -> "-"
        KeyEvent.VK_PLUS -> "+"
        KeyEvent.VK_SPACE -> "Space"
        KeyEvent.VK_ENTER -> "↵"
        KeyEvent.VK_TAB -> "⇥"
        KeyEvent.VK_ESCAPE -> "⎋"
        KeyEvent.VK_BACK_SPACE -> "⌫"
        KeyEvent.VK_DELETE -> "⌦"
        KeyEvent.VK_UP -> "↑"
        KeyEvent.VK_DOWN -> "↓"
        KeyEvent.VK_LEFT -> "←"
        KeyEvent.VK_RIGHT -> "→"
        KeyEvent.VK_PAGE_UP -> "⇞"
        KeyEvent.VK_PAGE_DOWN -> "⇟"
        KeyEvent.VK_HOME -> "⇱"
        KeyEvent.VK_END -> "⇲"
        else -> KeyEvent.getKeyText(keyCode)
    }
    badges.add(KeyBadge(keyText))
    
    return badges
}

