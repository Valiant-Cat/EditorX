package editorx.gui

import editorx.core.util.SystemUtils
import org.slf4j.LoggerFactory
import java.awt.Image
import java.awt.Taskbar
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.JFrame

abstract class AppFrame : JFrame() {

    init {
        // 设置应用图标
        setAppIcon("icon_round_128.png")
    }

    private fun setAppIcon(icon: String) {
        val logger = LoggerFactory.getLogger("GuiApp")
        val classLoader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
        val iconUrl = classLoader.getResource(icon) ?: run {
            logger.warn("未找到图标资源: $icon")
            return
        }

        val image = try {
            ImageIO.read(iconUrl)
        } catch (e: IOException) {
            logger.warn("无法读取图标图像", e)
            return
        }

        if (image == null) {
            logger.warn("图标图像为空")
            return
        }

        // 优先尝试现代 Taskbar API（Windows / 新版本的 macOS）
        if (Taskbar.isTaskbarSupported()) {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.iconImage = image
                logger.info("成功通过 Taskbar API 设置应用图标")
                return
            }
        }

        // macOS 专属回退（目前仍是主要方式）
        if (SystemUtils.isMacOS()) {
            runCatching {
                val appClass = Class.forName("com.apple.eawt.Application")
                val application = appClass.getMethod("getApplication").invoke(null)
                appClass.getMethod("setDockIconImage", Image::class.java).invoke(application, image)
                logger.info("成功通过 Apple EAWT 设置 macOS Dock 图标")
            }.onFailure { e ->
                logger.warn("设置 macOS Dock 图标失败", e)
            }
        }
    }
}