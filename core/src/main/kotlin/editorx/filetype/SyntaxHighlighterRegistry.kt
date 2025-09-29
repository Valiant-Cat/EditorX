package editorx.filetype

import editorx.lang.Language
import java.io.File

object SyntaxHighlighterRegistry {
    private val map: MutableMap<Language, SyntaxHighlighterFactory> = mutableMapOf()

    /**
     * 注册语法适配器
     */
    fun registerSyntaxHighlighterFactory(language: Language, factory: SyntaxHighlighterFactory) {
        map[language] = factory
    }

    /**
     * 获取文件对应的语法适配器
     */
    fun getSyntaxHighlighter(file: File): SyntaxHighlighter? {
        val fileType = FileTypeRegistry.getFileTypeByFileName(file.name)
        if (fileType is LanguageFileType) {
            return map[fileType.language]?.getSyntaxHighlighter(file)
        }
        return null
    }
}
