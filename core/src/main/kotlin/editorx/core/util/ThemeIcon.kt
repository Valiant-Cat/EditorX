package editorx.core.util

import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.Icon

/**
 * 根据主题自适应颜色的图标包装器
 * 在绘制时将图标颜色替换为主题的前景色（onSurface）
 * 如果组件被禁用，则使用禁用状态的颜色
 */
class ThemeIcon(
    private val baseIcon: Icon,
    private val getThemeColor: () -> Color,
    private val getDisabledColor: (() -> Color)? = null
) : Icon {
    override fun getIconWidth(): Int = baseIcon.iconWidth
    override fun getIconHeight(): Int = baseIcon.iconHeight

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2d = g.create() as Graphics2D
        try {
            // 先绘制到临时图像，用于颜色替换
            // 使用设备像素比来提高在高 DPI 屏幕上的清晰度
            val scaleFactor = if (c != null) {
                val config = c.graphicsConfiguration
                val transform = config?.defaultTransform
                if (transform != null && transform.scaleX > 1.0) transform.scaleX else 1.0
            } else {
                1.0
            }
            
            val imgWidth = (baseIcon.iconWidth * scaleFactor).toInt().coerceAtLeast(1)
            val imgHeight = (baseIcon.iconHeight * scaleFactor).toInt().coerceAtLeast(1)
            
            val img = BufferedImage(
                imgWidth,
                imgHeight,
                BufferedImage.TYPE_INT_ARGB
            )
            val imgG = img.createGraphics() as Graphics2D
            try {
                // 设置高质量的渲染提示，确保图标绘制清晰
                imgG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                imgG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                imgG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                imgG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                imgG.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
                imgG.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
                imgG.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
                
                // 如果使用了缩放因子，需要先缩放绘图上下文
                if (scaleFactor != 1.0) {
                    imgG.scale(scaleFactor, scaleFactor)
                }
                
                // 绘制原始图标
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
                getDisabledColor.invoke()
            } else {
                getThemeColor()
            }

            // 应用颜色过滤：将非透明像素替换为主题颜色，保持 alpha 通道
            val themeRgb = themeColor.rgb and 0x00FFFFFF
            val filteredImg = if (scaleFactor != 1.0 || img.width != baseIcon.iconWidth || img.height != baseIcon.iconHeight) {
                // 需要缩放或已经缩放，创建新的图像
                BufferedImage(
                    imgWidth,
                    imgHeight,
                    BufferedImage.TYPE_INT_ARGB
                ).apply {
                    // 替换颜色
                    for (py in 0 until img.height) {
                        for (px in 0 until img.width) {
                            val rgb = img.getRGB(px, py)
                            val alpha = (rgb shr 24) and 0xFF
                            val newRgb = if (alpha > 0) {
                                (alpha shl 24) or themeRgb
                            } else {
                                rgb
                            }
                            setRGB(px, py, newRgb)
                        }
                    }
                }
            } else {
                // 直接修改原图像，避免创建新图像
                for (py in 0 until img.height) {
                    for (px in 0 until img.width) {
                        val rgb = img.getRGB(px, py)
                        val alpha = (rgb shr 24) and 0xFF
                        if (alpha > 0) {
                            val newRgb = (alpha shl 24) or themeRgb
                            img.setRGB(px, py, newRgb)
                        }
                    }
                }
                img
            }
            
            // 设置高质量的图像绘制提示
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            // 如果需要缩放回原始尺寸，使用高质量缩放
            if (scaleFactor != 1.0) {
                g2d.drawImage(
                    filteredImg,
                    x, y,
                    baseIcon.iconWidth,
                    baseIcon.iconHeight,
                    null
                )
            } else {
                g2d.drawImage(filteredImg, x, y, null)
            }
        } finally {
            g2d.dispose()
        }
    }

}

