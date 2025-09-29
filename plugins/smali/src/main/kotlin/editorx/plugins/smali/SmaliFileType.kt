package editorx.plugins.smali

import editorx.filetype.LanguageFileType
import editorx.gui.IconRef

object SmaliFileType : LanguageFileType(SmaliLanguage) {
    override fun getName(): String = "smali"

    override fun getExtensions(): Array<String> = arrayOf("smali")

    override fun getIcon(): IconRef = SmaliIcons.SmaliFile
}
