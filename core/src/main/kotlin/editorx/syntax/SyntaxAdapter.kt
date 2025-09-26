package editorx.syntax

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea

interface SyntaxAdapter {
    val languageId: String           // e.g., "text/smali"
    val fileExtensions: Set<String>  // e.g., setOf(".smali")

    fun getTokenMakerProvider(): TokenMakerProvider

    fun configureTextArea(textArea: RSyntaxTextArea) {}
}
