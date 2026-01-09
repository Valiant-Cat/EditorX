package editorx.core.util

import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

object IconUtils {

    fun resizeIcon(icon: Icon, width: Int, height: Int): Icon {
        // 若尺寸一致，直接返回原图标，避免不必要的绘制与 hint 处理
        if (icon.iconWidth == width && icon.iconHeight == height) return icon

        return object : Icon {
            override fun getIconWidth(): Int = width
            override fun getIconHeight(): Int = height

            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2 = g.create() as Graphics2D
                try {
                    val oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION)
                    val oldRender = g2.getRenderingHint(RenderingHints.KEY_RENDERING)
                    val oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                    val oldTx = g2.transform

                    // 使用更高质量的插值算法
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    // 确保文本和图形渲染质量
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

                    val originalWidth = icon.iconWidth
                    val originalHeight = icon.iconHeight
                    val scaleX = width.toDouble() / originalWidth
                    val scaleY = height.toDouble() / originalHeight
                    
                    // 先平移后缩放，这样坐标计算更清晰
                    g2.translate(x.toDouble(), y.toDouble())
                    g2.scale(scaleX, scaleY)
                    // 在变换后的坐标系中，图标应在 (0, 0) 位置绘制
                    icon.paintIcon(c, g2, 0, 0)

                    g2.transform = oldTx
                    // 恢复渲染 hint：老值可能为 null，不能直接 setRenderingHint(null)
                    if (oldInterp != null) g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp)
                    else g2.getRenderingHints().remove(RenderingHints.KEY_INTERPOLATION)
                    if (oldRender != null) g2.setRenderingHint(RenderingHints.KEY_RENDERING, oldRender)
                    else g2.getRenderingHints().remove(RenderingHints.KEY_RENDERING)
                    if (oldAA != null) g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA)
                    else g2.getRenderingHints().remove(RenderingHints.KEY_ANTIALIASING)
                } finally {
                    g2.dispose()
                }
            }
        }
    }
}
