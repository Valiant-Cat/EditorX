package editorx.gui.plugin

import editorx.gui.ViewProvider
import editorx.gui.ui.MainWindow
import editorx.gui.widget.SvgIcon
import editorx.plugin.LoadedPlugin
import editorx.plugin.PluginContext
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.util.logging.Logger
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * GUI 实现的插件上下文
 * 为每个插件创建独立的实例，包含插件标识信息
 */
class GuiPluginContext(
    private val mainWindow: MainWindow,
    private val loadedPlugin: LoadedPlugin,
) : PluginContext {

    companion object {
        private const val ICON_SIZE = 24
    }

    private val logger =
        Logger.getLogger("${GuiPluginContext::class.java.name}[${loadedPlugin.name}-${loadedPlugin.version}]")

    override fun getLoadedPlugin(): LoadedPlugin {
        return loadedPlugin
    }

    override fun addActivityBarItem(iconPath: String, viewProvider: ViewProvider) {
        val icon = loadIcon(iconPath)
        mainWindow.activityBar.addItem(loadedPlugin.id, loadedPlugin.name, icon, viewProvider)
    }

    override fun openFile(file: File) {
        try {
            mainWindow.editor.openFile(file)
        } catch (e: Exception) {
            logger.warning("打开文件失败: ${file.name}, 错误: ${e.message}")
        }
    }

    private fun loadIcon(iconPath: String): Icon {
        return try {
            when {
                iconPath.isEmpty() -> createDefaultIcon()
                iconPath.endsWith(".svg") -> {
                    val svgIcon = SvgIcon.fromResource("/$iconPath")
                    svgIcon?.let { resizeIcon(it, ICON_SIZE, ICON_SIZE) } ?: run {
                        logger.warning("SVG图标资源未找到: $iconPath"); createDefaultIcon()
                    }
                }

                else -> {
                    val resource = javaClass.getResource("/$iconPath")
                    if (resource != null) {
                        val originalIcon = ImageIcon(resource)
                        resizeIcon(originalIcon, ICON_SIZE, ICON_SIZE)
                    } else {
                        logger.warning("图标资源未找到: $iconPath"); createDefaultIcon()
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning("加载图标失败: $iconPath, 错误: ${e.message}")
            createDefaultIcon()
        }
    }

    private fun resizeIcon(icon: Icon, width: Int, height: Int): Icon {
        return object : Icon {
            override fun getIconWidth(): Int = width
            override fun getIconHeight(): Int = height

            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                // 保存原始变换
                val originalTransform = g2d.transform

                // 计算缩放比例
                val originalWidth = icon.iconWidth
                val originalHeight = icon.iconHeight
                val scaleX = width.toDouble() / originalWidth
                val scaleY = height.toDouble() / originalHeight

                // 应用缩放变换
                g2d.scale(scaleX, scaleY)

                // 调整绘制位置以适应缩放
                val scaledX = x / scaleX
                val scaledY = y / scaleY

                // 绘制原始图标（现在会被缩放到目标尺寸）
                icon.paintIcon(c, g2d, scaledX.toInt(), scaledY.toInt())

                // 恢复原始变换
                g2d.transform = originalTransform
            }
        }
    }

    private fun createDefaultIcon(): Icon {
        return object : Icon {
            override fun getIconWidth(): Int = ICON_SIZE
            override fun getIconHeight(): Int = ICON_SIZE

            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = Color(108, 112, 126)
                g2d.fillRect(x + 2, y + 2, ICON_SIZE - 4, ICON_SIZE - 4)
            }
        }
    }
}
