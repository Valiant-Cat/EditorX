package editorx.plugins.json

import editorx.gui.IconRef
import editorx.lang.LanguageFileType

class JsonFIleType : LanguageFileType(JsonLanguage.getInstance()) {
    override fun getName(): String = "json"

    override fun getDescription(): String = "JSON files"

    override fun getExtensions(): Set<String> = setOf("json")

    override fun getIcon(): IconRef = JsonIcons.JsonFile
}