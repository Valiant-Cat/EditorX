package editorx.filetype

import editorx.lang.Language
import editorx.util.FileUtil

object FileTypeRegistry {
    private val myExtensionsMap: HashMap<String, FileType> = hashMapOf()
    private val myAllFileTypes: ArrayList<FileType> = arrayListOf()

    fun registerFileType(fileType: FileType) {
        myAllFileTypes.add(fileType)
        for (ext in fileType.getExtensions()) {
            myExtensionsMap[ext] = fileType
        }
    }

    fun findFileTypeByLanguage(language: Language): LanguageFileType? {
        return language.findMyFileType(getRegisteredFileTypes())
    }

    fun getRegisteredFileTypes(): Array<FileType> {
        return myAllFileTypes.toTypedArray()
    }

    fun getFileTypeByFileName(fileName: String): FileType? {
        return getFileTypeByExtension(FileUtil.getExtension(fileName));
    }

    fun getFileTypeByExtension(extension: String): FileType? {
        val result = myExtensionsMap[extension]
        return result
    }

    fun findFileTypeByName(fileTypeName: String): FileType? {
        for (type in myAllFileTypes) {
            if (type.getName() == fileTypeName) {
                return type
            }
        }
        return null
    }
}

