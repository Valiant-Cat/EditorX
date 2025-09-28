package editorx.plugins.smali

import editorx.gui.IconRef
import editorx.lang.LanguageFileType

class SmaliFileType : LanguageFileType(language = SmaliLanguage.getInstance()) {
    override fun getName(): String = "smali"

    override fun getDescription(): String = "Smali assembly files"

    override fun getExtensions(): Set<String> = setOf("smali")

    override fun getIcon(): IconRef = SmaliIcons.SmaliFile
}
