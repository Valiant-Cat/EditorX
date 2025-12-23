package editorx.core.filetype

import editorx.core.lang.Language
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory
import java.io.File

object SyntaxHighlighterRegistry {
    private data class Registration(
        val language: Language,
        val syntaxHighlighter: SyntaxHighlighter,
        val ownerId: String?,
    )

    private val registrations: MutableList<Registration> = mutableListOf()
    private val languageToRegistrations: MutableMap<Language, MutableList<Registration>> = mutableMapOf()

    /**
     * 注册语法适配器
     */
    fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter) {
        registerSyntaxHighlighter(language, syntaxHighlighter, ownerId = null)
    }

    /**
     * 注册语法适配器，并记录归属插件（用于卸载时回收）。
     */
    fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter, ownerId: String?) {
        val reg = Registration(language = language, syntaxHighlighter = syntaxHighlighter, ownerId = ownerId)
        registrations.add(reg)
        languageToRegistrations.getOrPut(language) { mutableListOf() }.add(reg)

        // 注册 TokenMaker
        val tmf = TokenMakerFactory.getDefaultInstance() as AbstractTokenMakerFactory
        tmf.putMapping(
            syntaxHighlighter.syntaxStyleKey,
            syntaxHighlighter.getTokenMakerClassName(),
            syntaxHighlighter::class.java.classLoader
        )
    }

    /**
     * 按插件 ID 卸载其注册的语法高亮。
     */
    fun unregisterByOwner(ownerId: String) {
        val toRemove = registrations.filter { it.ownerId == ownerId }
        if (toRemove.isEmpty()) return

        toRemove.forEach { removeTokenMakerMapping(it.syntaxHighlighter.syntaxStyleKey) }
        registrations.removeIf { it.ownerId == ownerId }
        rebuildLanguageIndex()
        // 兜底：对仍存在的高亮重新 putMapping，确保 TokenMaker 可用
        registrations.forEach { reg ->
            runCatching {
                val tmf = TokenMakerFactory.getDefaultInstance() as AbstractTokenMakerFactory
                tmf.putMapping(
                    reg.syntaxHighlighter.syntaxStyleKey,
                    reg.syntaxHighlighter.getTokenMakerClassName(),
                    reg.syntaxHighlighter::class.java.classLoader
                )
            }
        }
    }

    private fun rebuildLanguageIndex() {
        languageToRegistrations.clear()
        for (reg in registrations) {
            languageToRegistrations.getOrPut(reg.language) { mutableListOf() }.add(reg)
        }
    }

    private fun removeTokenMakerMapping(styleKey: String) {
        runCatching {
            val tmf = TokenMakerFactory.getDefaultInstance() as? AbstractTokenMakerFactory ?: return
            val field = AbstractTokenMakerFactory::class.java.getDeclaredField("tokenMakerMap")
            field.isAccessible = true
            val map = field.get(tmf) as? MutableMap<*, *> ?: return
            @Suppress("UNCHECKED_CAST")
            (map as MutableMap<String, Any?>).remove(styleKey)
        }
    }

    /**
     * 获取文件对应的语法适配器
     */
    fun getSyntaxHighlighter(file: File): SyntaxHighlighter? {
        val fileType = FileTypeRegistry.getFileTypeByFileName(file.name)
        if (fileType is LanguageFileType) {
            return languageToRegistrations[fileType.language]?.lastOrNull()?.syntaxHighlighter
        }
        return null
    }
}
