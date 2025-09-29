package editorx.plugins.json

import editorx.filetype.LanguageFileType
import editorx.gui.IconRef

object JsonFIleType : LanguageFileType(JsonLanguage) {

    override fun getName(): String = "json"

    override fun getExtensions(): Array<String> = arrayOf("json")

    override fun getIcon(): IconRef = JsonIcons.JsonFile
}