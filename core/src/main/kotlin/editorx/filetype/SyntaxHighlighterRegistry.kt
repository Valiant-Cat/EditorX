package editorx.filetype

import editorx.lang.Language
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory
import java.io.File

object SyntaxHighlighterRegistry {
    private val map: MutableMap<Language, SyntaxHighlighter> = mutableMapOf()

    /**
     * 注册语法适配器
     */
    fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter) {
        map[language] = syntaxHighlighter

        // 注册 TokenMaker
        val tmf = TokenMakerFactory.getDefaultInstance() as AbstractTokenMakerFactory
        tmf.putMapping(syntaxHighlighter.syntaxStyleKey, syntaxHighlighter.getTokenMakerClassName())
    }

    /**
     * 获取文件对应的语法适配器
     */
    fun getSyntaxHighlighter(file: File): SyntaxHighlighter? {
        val fileType = FileTypeRegistry.getFileTypeByFileName(file.name)
        if (fileType is LanguageFileType) {
            return map[fileType.language]
        }
        return null
    }
}
