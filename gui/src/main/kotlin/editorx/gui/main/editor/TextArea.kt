package editorx.gui.main.editor

import editorx.filetype.SyntaxHighlighterRegistry
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory
import java.awt.Font
import java.io.File

/**
 * 支持自定义语法高亮的文本区域
 */
class TextArea : RSyntaxTextArea() {

    init {
        // 设置默认字体
        font = Font("Consolas", Font.PLAIN, 14)
    }

    /**
     * 设置文件并应用自定义语法高亮
     */
    fun detectSyntax(file: File) {
        // 检测是否有自定义语法高亮器
        val syntaxHighlighter = SyntaxHighlighterRegistry.getSyntaxHighlighter(file)
        if (syntaxHighlighter != null) {
            println("找到自定义语法高亮器: ${syntaxHighlighter::class.simpleName}")
            val tmf = TokenMakerFactory.getDefaultInstance() as AbstractTokenMakerFactory
            tmf.putMapping(syntaxHighlighter.syntaxStyleKey, syntaxHighlighter.getTokenMakerClassName())
            this.syntaxEditingStyle = syntaxHighlighter.syntaxStyleKey
            this.isCodeFoldingEnabled = syntaxHighlighter.isCodeFoldingEnabled
            this.isBracketMatchingEnabled = syntaxHighlighter.isBracketMatchingEnabled
        } else {
            println("未找到自定义语法高亮器，使用默认语法")
            syntaxEditingStyle = detectDefaultSyntax(file)
        }
    }

    /**
     * 检测默认语法
     */
    private fun detectDefaultSyntax(file: File): String {
        return when {
            file.name.endsWith(".smali") -> "text/smali"
            file.name.endsWith(".xml") -> SyntaxConstants.SYNTAX_STYLE_XML
            file.name.endsWith(".java") -> SyntaxConstants.SYNTAX_STYLE_JAVA
            file.name.endsWith(".kt") -> SyntaxConstants.SYNTAX_STYLE_KOTLIN
            file.name.endsWith(".js") -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
            file.name.endsWith(".css") -> SyntaxConstants.SYNTAX_STYLE_CSS
            file.name.endsWith(".html") -> SyntaxConstants.SYNTAX_STYLE_HTML
            file.name.endsWith(".json") -> SyntaxConstants.SYNTAX_STYLE_JSON
            else -> SyntaxConstants.SYNTAX_STYLE_NONE
        }
    }
}