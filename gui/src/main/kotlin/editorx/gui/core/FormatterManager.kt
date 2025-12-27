package editorx.gui.core

import editorx.core.filetype.Formatter
import editorx.core.filetype.Language
import editorx.core.filetype.LanguageFileType
import java.io.File

object FormatterManager {
    private data class Registration(
        val language: Language,
        val formatter: Formatter,
        val ownerId: String?,
    )

    private val registrations: MutableList<Registration> = mutableListOf()
    private val languageToRegistrations: MutableMap<Language, MutableList<Registration>> = mutableMapOf()

    /**
     * 注册格式化器
     */
    fun registerFormatter(language: Language, formatter: Formatter) {
        registerFormatter(language, formatter, ownerId = null)
    }

    /**
     * 注册格式化器，并记录归属插件（用于卸载时回收）。
     */
    fun registerFormatter(language: Language, formatter: Formatter, ownerId: String?) {
        val reg = Registration(language = language, formatter = formatter, ownerId = ownerId)
        registrations.add(reg)
        languageToRegistrations.getOrPut(language) { mutableListOf() }.add(reg)
    }

    /**
     * 按插件 ID 卸载其注册的格式化器。
     */
    fun unregisterByOwner(ownerId: String) {
        registrations.removeIf { it.ownerId == ownerId }
        rebuildLanguageIndex()
    }

    private fun rebuildLanguageIndex() {
        languageToRegistrations.clear()
        for (reg in registrations) {
            languageToRegistrations.getOrPut(reg.language) { mutableListOf() }.add(reg)
        }
    }

    /**
     * 获取文件对应的格式化器
     */
    fun getFormatter(file: File): Formatter? {
        val fileType = FileTypeManager.getFileTypeByFileName(file.name)
        if (fileType is LanguageFileType) {
            return languageToRegistrations[fileType.language]?.lastOrNull()?.formatter
        }
        return null
    }
}

