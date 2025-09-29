package editorx.filetype

interface SyntaxHighlighter {
    val syntaxStyleKey: String           // e.g., "text/smali"
    val fileExtensions: Set<String>      // e.g., setOf(".smali")

    /**
     * 是否支持代码折叠
     */
    val isCodeFoldingEnabled: Boolean

    /**
     * 是否启用括号匹配
     */
    val isBracketMatchingEnabled: Boolean

    /**
     * 获取 TokenMaker 类名
     */
    fun getTokenMakerClassName(): String
}