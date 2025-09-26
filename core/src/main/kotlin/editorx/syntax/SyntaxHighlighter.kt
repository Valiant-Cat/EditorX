package editorx.syntax

import java.awt.Color

/**
 * 高亮片段
 */
data class HighlightSpan(
    val start: Int,
    val end: Int,
    val color: Color
)

/**
 * 语法高亮器接口
 * 负责对文本进行语法高亮处理
 */
interface SyntaxHighlighter {

    /**
     * 对单行文本进行语法高亮
     * @param line 要高亮的文本行
     * @return 高亮片段列表
     */
    fun highlight(line: String): List<HighlightSpan> {
        return emptyList() // 默认无高亮
    }
}
