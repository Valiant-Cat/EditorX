package editorx.core.filetype

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

abstract class Language(val id: String) {

    init {
        // 确保 ID 全局唯一
        if (registeredIds.containsKey(id)) {
            throw IllegalStateException("Language with id '$id' is already registered: ${registeredIds[id]}")
        }
        registeredIds[id] = this
    }

    fun getDisplayName(): String = id

    fun findMyFileType(types: Array<FileType>): LanguageFileType? {
        for (fileType in types) {
            if (fileType is LanguageFileType) {
                if (fileType.language === this) {
                    return fileType
                }
            }
        }
        return null
    }

    companion object {
        // 注册表：ID -> Language
        private val registeredIds: ConcurrentMap<String, Language> = ConcurrentHashMap()

        /**
         * 通过语言 ID 查找 Language 实例
         */
        fun findLanguageByID(id: String): Language? {
            return registeredIds[id]
        }

        /**
         * 获取所有已注册的语言（用于调试或枚举）
         */
        fun getRegisteredLanguages(): Collection<Language> {
            return registeredIds.values
        }
    }
}

