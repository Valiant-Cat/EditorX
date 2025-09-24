package editor.gui.plugin

import editor.gui.ViewProvider
import editor.gui.ui.activitybar.ActivityBar
import editor.gui.ui.editor.Editor
import editor.gui.ui.sidebar.SideBar
import editor.gui.ui.SvgIcon
import editor.plugin.PluginContext
import editor.gui.ui.panel.Panel
import java.awt.*
import java.io.File
import java.util.logging.Logger
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * GUI 实现的插件上下文
 */
class GuiPluginContext(
    private val activityBar: ActivityBar,
    private val sideBar: SideBar,
    private val panel: Panel,
    private val editor: Editor
) : PluginContext {
    private val logger = Logger.getLogger(GuiPluginContext::class.java.name)

    override fun addActivityBarItem(id: String, iconPath: String, tooltip: String, viewProvider: ViewProvider) {
        try {
            val icon = loadIcon(iconPath)
            activityBar.registerItem(id, icon ?: ImageIcon(), tooltip, viewProvider)
            logger.info("布局提供器注册成功: $id")
        } catch (e: Exception) {
            logger.warning("注册布局提供器失败: $id, 错误: ${e.message}")
            activityBar.registerItem(id, ImageIcon(), tooltip, viewProvider)
        }
    }

    override fun openFile(file: File) {
        try {
            editor.openFile(file)
            logger.info("文件已打开: ${file.name}")
        } catch (e: Exception) {
            logger.warning("打开文件失败: ${file.name}, 错误: ${e.message}")
        }
    }

    private fun loadIcon(iconPath: String): Icon? {
        return try {
            when {
                iconPath.isEmpty() -> createDefaultIcon()
                iconPath.endsWith(".svg") -> {
                    val svgIcon = SvgIcon.fromResource("/$iconPath")
                    svgIcon ?: run {
                        logger.warning("SVG图标资源未找到: $iconPath"); createDefaultIcon()
                    }
                }

                else -> {
                    val resource = javaClass.getResource("/$iconPath")
                    if (resource != null) ImageIcon(resource) else {
                        logger.warning("图标资源未找到: $iconPath"); createDefaultIcon()
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning("加载图标失败: $iconPath, 错误: ${e.message}")
            createDefaultIcon()
        }
    }

    private fun createDefaultIcon(): Icon {
        return object : Icon {
            override fun getIconWidth(): Int = 16
            override fun getIconHeight(): Int = 16

            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = Color(108, 112, 126)
                g2d.fillRoundRect(x + 2, y + 4, 12, 10, 2, 2)
                g2d.fillRoundRect(x + 2, y + 2, 8, 4, 1, 1)
            }
        }
    }
}

