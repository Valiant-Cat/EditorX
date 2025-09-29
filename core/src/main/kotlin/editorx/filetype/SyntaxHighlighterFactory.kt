package editorx.filetype

import java.io.File


interface SyntaxHighlighterFactory {

    fun getSyntaxHighlighter(file: File?): SyntaxHighlighter
}