package editorx.syntax

/**
 * 语法高亮器接口
 * 负责对文本进行语法高亮处理
 */
interface SyntaxHighlighter {

    /**
     * 对单行文本进行语法高亮
     * @param line 要高亮的文本行
     * @return 高亮后的文本（可以是HTML、ANSI转义序列等格式）
     */
    fun highlight(line: String): String {
        return line // 默认无高亮
    }
}
