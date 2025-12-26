package editorx.core.util

import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.Icon

/**
 * 根据主题自适应颜色的图标包装器
 * 在绘制时将图标颜色替换为主题的前景色（onSurface）
 * 如果组件被禁用，则使用禁用状态的颜色
 */
class ThemeAdaptiveIcon(
    private val baseIcon: Icon,
    private val getThemeColor: () -> Color,
    private val getDisabledColor: (() -> Color)? = null
) : Icon {
    override fun getIconWidth(): Int = baseIcon.iconWidth
    override fun getIconHeight(): Int = baseIcon.iconHeight

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2d = g.create() as Graphics2D
        try {
            // 先绘制到临时图像
            val img = BufferedImage(
                baseIcon.iconWidth,
                baseIcon.iconHeight,
                BufferedImage.TYPE_INT_ARGB
            )
            val imgG = img.createGraphics()
            try {
                baseIcon.paintIcon(c, imgG, 0, 0)
            } finally {
                imgG.dispose()
            }

            // 检查组件是否被禁用
            val isDisabled = c?.let { component ->
                when (component) {
                    is javax.swing.AbstractButton -> !component.isEnabled
                    else -> false
                }
            } ?: false

            // 根据禁用状态选择颜色
            val themeColor = if (isDisabled && getDisabledColor != null) {
                getDisabledColor()
            } else {
                getThemeColor()
            }

            // 应用颜色过滤：将非透明像素替换为主题颜色
            val filteredImg = BufferedImage(
                baseIcon.iconWidth,
                baseIcon.iconHeight,
                BufferedImage.TYPE_INT_ARGB
            )
            
            for (px in 0 until img.width) {
                for (py in 0 until img.height) {
                    val rgb = img.getRGB(px, py)
                    val alpha = (rgb shr 24) and 0xFF
                    val newRgb = if (alpha > 0) {
                        // 将颜色替换为主题颜色，保持原始 alpha
                        (alpha shl 24) or (themeColor.rgb and 0x00FFFFFF)
                    } else {
                        // 保持透明
                        rgb
                    }
                    filteredImg.setRGB(px, py, newRgb)
                }
            }
            
            g2d.drawImage(filteredImg, x, y, null)
        } finally {
            g2d.dispose()
        }
    }

}

