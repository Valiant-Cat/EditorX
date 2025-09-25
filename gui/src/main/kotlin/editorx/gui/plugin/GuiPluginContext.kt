package editorx.gui.plugin
import editorx.gui.ViewProvider
import editorx.gui.ui.MainWindow
import editorx.gui.widget.SvgIcon
import editorx.command.CommandRegistry
import editorx.event.EventBus
import editorx.plugin.LoadedPlugin
import editorx.plugin.PluginContext
import editorx.settings.SettingsStore
import editorx.workspace.WorkspaceManager
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

    override fun commands(): CommandRegistry = mainWindow.services.commands
    override fun eventBus(): EventBus = mainWindow.services.eventBus
    override fun settings(): SettingsStore = mainWindow.services.settings
    override fun workspace(): WorkspaceManager = mainWindow.services.workspace

    private fun loadIcon(iconPath: String): Icon {
        return try {
            when {
                iconPath.isEmpty() -> createDefaultIcon()
                iconPath.endsWith(".svg") -> {
                    val resPath = if (iconPath.startsWith("/")) iconPath else "/$iconPath"
                    SvgIcon.fromResource(resPath, ICON_SIZE, ICON_SIZE) ?: createDefaultIcon()
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
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp)
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, oldRender)
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA)
                } finally {
                    g2.dispose()
                }
            }
        }
    }

    private fun createDefaultIcon(): Icon {
        return object : Icon {
            override fun getIconWidth(): Int = ICON_SIZE
            override fun getIconHeight(): Int = ICON_SIZE

            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2 = g.create() as Graphics2D
                try {
                    val oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                    val oldPaint = g2.paint
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = Color(108, 112, 126)
                    g2.fillRect(x + 2, y + 2, ICON_SIZE - 4, ICON_SIZE - 4)
                    g2.paint = oldPaint
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA)
                } finally {
                    g2.dispose()
                }
            }
        }
    }
}
