package editorx.plugins.xml

import editorx.core.filetype.Formatter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter

/**
 * XML 格式化器
 */
object XmlFormatter : Formatter {
    override fun format(content: String): String {
        if (content.isBlank()) return content

        return try {
            // 解析 XML
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(content)))

            // 格式化输出
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")

            val writer = StringWriter()
            transformer.transform(DOMSource(doc), StreamResult(writer))
            writer.toString()
        } catch (e: Exception) {
            // 如果解析失败，尝试简单的缩进格式化
            formatSimple(content)
        }
    }

    /**
     * 简单的 XML 格式化（当标准解析失败时使用）
     */
    private fun formatSimple(xml: String): String {
        val lines = xml.lines()
        if (lines.isEmpty()) return xml

        // 找到所有非空行的最小缩进量（公共前导空格数）
        val minIndent = lines
            .filter { it.isNotBlank() }
            .map { line -> line.takeWhile { it == ' ' }.length }
            .minOrNull() ?: 0

        // 移除公共缩进
        val unindentedLines = lines.map { line ->
            when {
                line.isBlank() -> ""
                line.length >= minIndent -> line.substring(minIndent)
                else -> line.trimStart()
            }
        }

        // 重新计算缩进：根标签从第0列开始，子元素每级缩进4个空格
        val result = mutableListOf<String>()
        var indentLevel = 0

        for (line in unindentedLines) {
            if (line.isBlank()) {
                result.add("")
                continue
            }

            val trimmedLine = line.trim()

            // 如果是结束标签，先减少缩进级别再添加
            if (trimmedLine.startsWith("</")) {
                indentLevel = maxOf(0, indentLevel - 1)
            }

            // 添加当前行
            result.add("    ".repeat(indentLevel) + trimmedLine)

            // 如果是开始标签且不是自闭合标签，增加缩进级别
            if (trimmedLine.startsWith("<") && !trimmedLine.startsWith("</") && !trimmedLine.endsWith("/>")) {
                indentLevel++
            }
        }

        return result.joinToString("\n")
    }
}

