package editorx.gui.main.editor

import editorx.syntax.SyntaxHighlighter
import editorx.syntax.SyntaxHighlighterManager
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import java.awt.Font
import java.io.File
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Highlighter

/**
 * 支持自定义语法高亮的文本区域
 */
class CustomSyntaxTextArea : RSyntaxTextArea() {
    
    private var customHighlighter: SyntaxHighlighter? = null
    private var highlighter: Highlighter? = null
    
    init {
        // 设置默认字体
        font = Font("Consolas", Font.PLAIN, 14)
        
        // 设置默认语法样式
        syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_NONE
        
        // 获取高亮器
        highlighter = getHighlighter()
    }
    
    /**
     * 设置文件并应用自定义语法高亮
     */
    fun setFile(file: File) {
        // 检测是否有自定义语法高亮器
        val customHighlighter = SyntaxHighlighterManager.getHighlighterForFile(file)
        if (customHighlighter != null) {
            this.customHighlighter = customHighlighter
            println("找到自定义语法高亮器: ${customHighlighter::class.simpleName}")
            // 延迟应用高亮，等待文本加载完成
            javax.swing.SwingUtilities.invokeLater {
                applyCustomSyntaxHighlighting()
            }
        } else {
            println("未找到自定义语法高亮器，使用默认语法")
            // 使用默认的语法检测
            syntaxEditingStyle = detectDefaultSyntax(file)
        }
    }
    
    /**
     * 应用自定义语法高亮
     */
    private fun applyCustomSyntaxHighlighting() {
        val highlighter = this.highlighter ?: return
        val customHighlighter = this.customHighlighter ?: return
        
        // 清除现有高亮
        highlighter.removeAllHighlights()
        
        try {
            val text = getText()
            if (text.isEmpty()) {
                println("文本为空，跳过高亮")
                return
            }
            
            println("开始应用高亮，文本长度: ${text.length}")
            
            // 应用简单的黄色高亮作为测试
            val painter = DefaultHighlighter.DefaultHighlightPainter(java.awt.Color.YELLOW)
            highlighter.addHighlight(0, text.length, painter)
            
            println("高亮应用完成")
        } catch (e: Exception) {
            e.printStackTrace()
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