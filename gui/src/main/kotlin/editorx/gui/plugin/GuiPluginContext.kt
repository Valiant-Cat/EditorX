package editorx.gui.plugin

import editorx.gui.ViewProvider
import editorx.gui.ui.MainWindow
import editorx.gui.widget.SvgIcon
import editorx.plugin.LoadedPlugin
import editorx.plugin.PluginContext
import java.awt.*
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
    private val logger =
        Logger.getLogger("${GuiPluginContext::class.java.name}[${loadedPlugin.name}-${loadedPlugin.version}]")

    override fun getLoadedPlugin(): LoadedPlugin {
        return loadedPlugin
    }

    override fun addActivityBarItem(id: String, iconPath: String, tooltip: String, viewProvider: ViewProvider) {
        try {
            val icon = loadIcon(iconPath)
            // 为每个插件创建唯一的ID，避免冲突
            val uniqueId = "${loadedPlugin.name}_$id"
            mainWindow.activityBar.registerItem(uniqueId, icon ?: ImageIcon(), tooltip, viewProvider)
            logger.info("插件[${loadedPlugin.name}] 布局提供器注册成功: $id -> $uniqueId")
        } catch (e: Exception) {
            logger.warning("插件[${loadedPlugin.name}] 注册布局提供器失败: $id, 错误: ${e.message}")
            val uniqueId = "${loadedPlugin.name}_$id"
            mainWindow.activityBar.registerItem(uniqueId, ImageIcon(), tooltip, viewProvider)
        }
    }

    override fun openFile(file: File) {
        try {
            mainWindow.editor.openFile(file)
            logger.info("插件[${loadedPlugin.name}] 文件已打开: ${file.name}")
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
