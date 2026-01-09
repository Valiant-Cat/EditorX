package editorx.core.util

import java.net.URL
import javax.swing.Icon
import javax.swing.ImageIcon

object IconLoader {
    /**
     * 尝试从多个 ClassLoader 查找资源
     * 查找顺序：
     * 1. 传入的 ClassLoader（如果提供）
     * 2. icons 模块的 ClassLoader（如果可用）
     * 3. IconLoader 的 ClassLoader
     * 4. 系统 ClassLoader
     */
    private fun findResource(resourcePath: String, classLoader: ClassLoader?): URL? {
        val normalized =
            if (resourcePath.startsWith("/")) resourcePath.substring(1) else resourcePath
        
        // 1. 优先使用传入的 ClassLoader
        classLoader?.getResource(normalized)?.let { return it }
        
        // 2. 尝试从 icons 模块的 ClassLoader 查找
        // 通过尝试加载 icons 模块的资源来获取其 ClassLoader
        runCatching {
            // 尝试通过资源路径推断 icons 模块的 ClassLoader
            // 如果资源路径包含 icons/common/ 或 icons/gui/，说明可能是从 icons 模块加载
            val iconsClassLoader = findIconsModuleClassLoader()
            iconsClassLoader?.getResource(normalized)?.let { return it }
        }
        
        // 3. 使用 IconLoader 的 ClassLoader
        IconLoader::class.java.classLoader?.getResource(normalized)?.let { return it }
        
        // 4. 使用系统 ClassLoader
        ClassLoader.getSystemClassLoader().getResource(normalized)?.let { return it }
        
        return null
    }
    
    /**
     * 尝试找到 icons 模块的 ClassLoader
     * 通过查找 icons 模块中的资源来确定其 ClassLoader
     */
    private fun findIconsModuleClassLoader(): ClassLoader? {
        // 尝试查找 icons 模块中的已知资源
        val testResource = "icons/common/folder.svg"
        val classLoaders = listOfNotNull(
            IconLoader::class.java.classLoader,
            ClassLoader.getSystemClassLoader(),
            Thread.currentThread().contextClassLoader
        )
        
        for (cl in classLoaders) {
            if (cl.getResource(testResource) != null) {
                return cl
            }
        }
        return null
    }

    /**
     * 加载图标
     * @param iconRef 图标引用
     * @param size 图标大小，默认为 16
     * @param adaptToTheme 是否根据主题自适应颜色，默认为 false
     * @param getThemeColor 获取主题颜色的函数，仅在 adaptToTheme=true 时使用
     * @param getDisabledColor 获取禁用状态颜色的函数，仅在 adaptToTheme=true 时使用，如果为 null 则使用 getThemeColor
     */
    fun getIcon(
        iconRef: IconRef, 
        size: Int = 16,
        adaptToTheme: Boolean = false,
        getThemeColor: (() -> java.awt.Color)? = null,
        getDisabledColor: (() -> java.awt.Color)? = null
    ): Icon? {
        val icon = if (iconRef.isSvg) loadSvg(iconRef, size) else loadRaster(iconRef, size)
        
        if (adaptToTheme && icon != null && getThemeColor != null) {
            return ThemeIcon(icon, getThemeColor, getDisabledColor)
        }
        
        return icon
    }

    private fun loadRaster(iconRef: IconRef, size: Int): Icon? {
        val url = findResource(iconRef.resourcePath, iconRef.classLoader) ?: return null
        return IconUtils.resizeIcon(ImageIcon(url), size, size)
    }

    private fun loadSvg(iconRef: IconRef, size: Int): Icon? {
        val url: URL = findResource(iconRef.resourcePath, iconRef.classLoader) 
            ?: return SvgIcon.fromResource(
                iconRef.resourcePath,
                iconRef.classLoader,
                size,
                size
            )

        // Prefer FlatLaf's robust SVG renderer if available
        // FlatSVGIcon 支持通过 derive 方法设置尺寸，避免双重缩放
        runCatching {
            val clazz = Class.forName("com.formdev.flatlaf.extras.FlatSVGIcon")
            val ctor = clazz.getConstructor(URL::class.java)
            val icon = ctor.newInstance(url) as Icon
            
            // 尝试使用 derive 方法直接设置尺寸（避免双重缩放）
            runCatching {
                val deriveMethod = clazz.getMethod("derive", Float::class.java, Float::class.java)
                val derivedIcon = deriveMethod.invoke(icon, size.toFloat(), size.toFloat()) as? Icon
                if (derivedIcon != null && derivedIcon.iconWidth == size && derivedIcon.iconHeight == size) {
                    return derivedIcon
                }
            }
            
            // 如果 derive 失败或尺寸不匹配，检查原始图标尺寸
            // 如果已经是目标尺寸，直接返回，避免不必要的缩放
            if (icon.iconWidth == size && icon.iconHeight == size) {
                return icon
            }
            
            // 否则才进行缩放
            return IconUtils.resizeIcon(icon, size, size)
        }

        // Fallback to plain SVGIcon if present
        runCatching {
            val clazz = Class.forName("com.formdev.svg.SVGIcon")
            val ctor = clazz.getConstructor(URL::class.java)
            val icon = ctor.newInstance(url) as Icon
            
            // 检查是否已经是目标尺寸
            if (icon.iconWidth == size && icon.iconHeight == size) {
                return icon
            }
            
            return IconUtils.resizeIcon(icon, size, size)
        }

        // Last resort: our minimal SvgIcon parser
        return SvgIcon.fromResource(iconRef.resourcePath, iconRef.classLoader, size, size)
    }
}
