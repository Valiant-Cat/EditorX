package editorx.gui.widget

import java.awt.*
import java.awt.geom.*
import javax.swing.Icon
import java.io.InputStream
import java.util.regex.Pattern

class SvgIcon(
    private val svgContent: String,
    private val width: Int = 16,
    private val height: Int = 16
) : Icon {
    private val pathData: List<SvgPath> = parseSvgPaths()
    override fun getIconWidth(): Int = width
    override fun getIconHeight(): Int = height
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        val originalTransform = g2d.transform
        val scaleX = width.toDouble() / 16.0
        val scaleY = height.toDouble() / 16.0
        g2d.translate(x.toDouble(), y.toDouble())
        g2d.scale(scaleX, scaleY)
        for (path in pathData) path.draw(g2d)
        g2d.transform = originalTransform
    }
    private fun parseSvgPaths(): List<SvgPath> {
        val paths = mutableListOf<SvgPath>()
        val pathPattern = Pattern.compile("<path[^>]*d=\"([^\"]+)\"[^>]*fill=\"([^\"]+)\"[^>]*stroke=\"([^\"]+)\"[^>]*/?>")
        val matcher = pathPattern.matcher(svgContent)
        while (matcher.find()) {
            val d = matcher.group(1); val fill = matcher.group(2); val stroke = matcher.group(3)
            if (d != null) paths.add(SvgPath(d, fill, stroke))
        }
        return paths
    }
    private class SvgPath(private val pathData: String, private val fillColor: String?, private val strokeColor: String?) {
        fun draw(g2d: Graphics2D) {
            try {
                val path = parsePathData(pathData)
                if (fillColor != null && fillColor != "none") { g2d.color = parseColor(fillColor); g2d.fill(path) }
                if (strokeColor != null && strokeColor != "none") { g2d.color = parseColor(strokeColor); g2d.stroke = BasicStroke(1.0f); g2d.draw(path) }
            } catch (_: Exception) { }
        }
        private fun parsePathData(pathData: String): Path2D.Double {
            val path = Path2D.Double()
            val commandPattern = Pattern.compile("([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)")
            val matcher = commandPattern.matcher(pathData)
            var currentX = 0.0; var currentY = 0.0
            while (matcher.find()) {
                val command = matcher.group(1)
                val params = matcher.group(2).trim()
                if (params.isEmpty()) continue
                val numbers = params.split("[\\s,]+".toRegex()).mapNotNull { it.toDoubleOrNull() }
                when (command.uppercase()) {
                    "M" -> {
                        if (numbers.size >= 2) {
                            currentX = numbers[0]; currentY = numbers[1]
                            path.moveTo(currentX, currentY)
                            var i = 2
                            while (i < numbers.size) {
                                if (i + 1 < numbers.size) { currentX = numbers[i]; currentY = numbers[i + 1]; path.lineTo(currentX, currentY); i += 2 } else break
                            }
                        }
                    }
                    "L" -> { var i = 0; while (i < numbers.size) { if (i + 1 < numbers.size) { currentX = numbers[i]; currentY = numbers[i + 1]; path.lineTo(currentX, currentY); i += 2 } else break } }
                    "H" -> { for (x in numbers) { currentX = x; path.lineTo(currentX, currentY) } }
                    "V" -> { for (y in numbers) { currentY = y; path.lineTo(currentX, currentY) } }
                    "C" -> { var i = 0; while (i < numbers.size) { if (i + 5 < numbers.size) { val x1 = numbers[i]; val y1 = numbers[i+1]; val x2 = numbers[i+2]; val y2 = numbers[i+3]; currentX = numbers[i+4]; currentY = numbers[i+5]; path.curveTo(x1, y1, x2, y2, currentX, currentY); i += 6 } else break } }
                    "Z" -> { path.closePath() }
                }
            }
            return path
        }
        private fun parseColor(colorStr: String): Color = when {
            colorStr.startsWith("#") -> {
                val hex = colorStr.substring(1)
                when (hex.length) {
                    3 -> Color(hex.substring(0,1).repeat(2).toInt(16), hex.substring(1,2).repeat(2).toInt(16), hex.substring(2,3).repeat(2).toInt(16))
                    6 -> Color(hex.substring(0,2).toInt(16), hex.substring(2,4).toInt(16), hex.substring(4,6).toInt(16))
                    else -> Color.BLACK
                }
            }
            colorStr == "none" -> Color(0,0,0,0)
            else -> {
                val rgbPattern = Pattern.compile("rgb\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)")
                val matcher = rgbPattern.matcher(colorStr)
                if (matcher.find()) Color(matcher.group(1).toInt(), matcher.group(2).toInt(), matcher.group(3).toInt()) else Color.BLACK
            }
        }
    }
    companion object {
        fun fromResource(resourcePath: String, width: Int = 16, height: Int = 16): SvgIcon? = try {
            val inputStream: InputStream? = SvgIcon::class.java.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                val content = inputStream.bufferedReader().use { it.readText() }
                inputStream.close(); SvgIcon(content, width, height)
            } else null
        } catch (e: Exception) { System.err.println("加载SVG资源失败: $resourcePath, 错误: ${e.message}"); null }
    }
}
