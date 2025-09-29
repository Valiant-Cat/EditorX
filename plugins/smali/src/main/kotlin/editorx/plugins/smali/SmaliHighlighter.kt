package editorx.plugins.smali

import editorx.filetype.SyntaxHighlighter

class SmaliHighlighter : SyntaxHighlighter {
    override val syntaxStyleKey: String = "text/smali"
    override val fileExtensions: Set<String> = setOf("smali")
    override val isCodeFoldingEnabled: Boolean = true
    override val isBracketMatchingEnabled: Boolean = true

    override fun getTokenMakerClassName(): String {
        return SmaliTokenMaker::class.java.name
    }
}