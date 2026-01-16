package editorx.gui.workbench.editor

import editorx.gui.core.SyntaxHighlighterManager
import editorx.gui.theme.Theme
import editorx.gui.theme.ThemeManager
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Theme as RSyntaxTheme
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Font
import java.io.File

/**
 * 支持自定义语法高亮的文本区域
 */
class TextArea : RSyntaxTextArea() {

    companion object {
        private val logger = LoggerFactory.getLogger(TextArea::class.java)
        private const val DARK_THEME_RESOURCE = "org/fife/ui/rsyntaxtextarea/themes/dark.xml"
        private const val LIGHT_THEME_RESOURCE = "org/fife/ui/rsyntaxtextarea/themes/default.xml"

        private val darkSyntaxTheme: RSyntaxTheme? by lazy { loadSyntaxTheme(DARK_THEME_RESOURCE) }
        private val lightSyntaxTheme: RSyntaxTheme? by lazy { loadSyntaxTheme(LIGHT_THEME_RESOURCE) }

        private fun loadSyntaxTheme(resource: String): RSyntaxTheme? {
            return runCatching {
                TextArea::class.java.classLoader.getResourceAsStream(resource)?.use { RSyntaxTheme.load(it) }
            }.onFailure {
                logger.warn("加载语法高亮主题失败: {}", resource, it)
            }.getOrNull()
        }

        private fun withAlpha(color: Color, alpha: Int): Color {
            val a = alpha.coerceIn(0, 255)
            return Color(color.red, color.green, color.blue, a)
        }
    }

    init {
        // 设置默认字体
        font = Font("Consolas", Font.PLAIN, 14)
        applyEditorTheme(ThemeManager.currentTheme)
    }

    fun applyEditorTheme(theme: Theme = ThemeManager.currentTheme) {
        val syntaxTheme = if (theme is Theme.Dark) darkSyntaxTheme else lightSyntaxTheme
        if (syntaxTheme != null) {
            syntaxTheme.apply(this)
        }

        background = theme.editorBackground
        foreground = theme.onSurface
        caretColor = theme.onSurface
        selectionColor = theme.primaryContainer
        selectedTextColor = theme.onPrimaryContainer
        setHighlightCurrentLine(true)
        setFadeCurrentLineHighlight(false)
        setCurrentLineHighlightColor(withAlpha(theme.surfaceVariant, 0x33))
    }

    /**
     * 设置文件并应用自定义语法高亮
     */
    fun detectSyntax(file: File) {
        // 检测是否有自定义语法高亮器
        val syntaxHighlighter = SyntaxHighlighterManager.getSyntaxHighlighter(file)
        if (syntaxHighlighter != null) {
            logger.debug("找到自定义语法高亮器: {}", syntaxHighlighter::class.simpleName)
            this.syntaxEditingStyle = syntaxHighlighter.syntaxStyleKey
            this.isCodeFoldingEnabled = syntaxHighlighter.isCodeFoldingEnabled
            this.isBracketMatchingEnabled = syntaxHighlighter.isBracketMatchingEnabled
        } else {
            logger.debug("未找到自定义语法高亮器，使用默认语法")
            syntaxEditingStyle = detectDefaultSyntax(file)
        }
    }

    /**
     * 检测默认语法
     */
    private fun detectDefaultSyntax(file: File): String {
        return when {
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
