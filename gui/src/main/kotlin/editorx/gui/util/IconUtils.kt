package editorx.gui.util

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

                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                    val originalWidth = icon.iconWidth
                    val originalHeight = icon.iconHeight
                    val scaleX = width.toDouble() / originalWidth
                    val scaleY = height.toDouble() / originalHeight
                    g2.scale(scaleX, scaleY)
                    val scaledX = x / scaleX
                    val scaledY = y / scaleY
                    icon.paintIcon(c, g2, scaledX.toInt(), scaledY.toInt())

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
