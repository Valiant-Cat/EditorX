package editorx.core.filetype

import editorx.core.lang.Language
import editorx.core.util.FileUtils

object FileTypeRegistry {
    private data class Registration(
        val fileType: FileType,
        val ownerId: String?,
    )

    private val registrations: MutableList<Registration> = mutableListOf()
    private val extensionToRegistrations: MutableMap<String, MutableList<Registration>> = mutableMapOf()

    fun registerFileType(fileType: FileType) {
        registerFileType(fileType, ownerId = null)
    }

    /**
     * 注册文件类型，并记录归属插件（用于卸载时回收）。
     */
    fun registerFileType(fileType: FileType, ownerId: String?) {
        val reg = Registration(fileType = fileType, ownerId = ownerId)
        registrations.add(reg)
        for (ext in fileType.getExtensions()) {
            extensionToRegistrations.getOrPut(ext) { mutableListOf() }.add(reg)
        }
    }

    /**
     * 按插件 ID 卸载其注册的文件类型。
     */
    fun unregisterByOwner(ownerId: String) {
        if (registrations.none { it.ownerId == ownerId }) return
        registrations.removeIf { it.ownerId == ownerId }
        rebuildExtensionIndex()
    }

    private fun rebuildExtensionIndex() {
        extensionToRegistrations.clear()
        for (reg in registrations) {
            for (ext in reg.fileType.getExtensions()) {
                extensionToRegistrations.getOrPut(ext) { mutableListOf() }.add(reg)
            }
        }
    }

    fun findFileTypeByLanguage(language: Language): LanguageFileType? {
        return language.findMyFileType(getRegisteredFileTypes())
    }

    fun getRegisteredFileTypes(): Array<FileType> {
        return registrations.map { it.fileType }.toTypedArray()
    }

    fun getFileTypeByFileName(fileName: String): FileType? {
        return getFileTypeByExtension(FileUtils.getExtension(fileName));
    }

    fun getFileTypeByExtension(extension: String): FileType? {
        return extensionToRegistrations[extension]?.lastOrNull()?.fileType
    }

    fun findFileTypeByName(fileTypeName: String): FileType? {
        return registrations.firstOrNull { it.fileType.getName() == fileTypeName }?.fileType
    }
}
