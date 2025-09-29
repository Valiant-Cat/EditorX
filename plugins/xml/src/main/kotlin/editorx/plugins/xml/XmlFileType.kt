import editorx.filetype.LanguageFileType
import editorx.gui.IconRef
import editorx.plugins.xml.XmlIcons

object XmlFileType : LanguageFileType(XmlLanguage) {
    override fun getName(): String = "xml"

    override fun getExtensions(): Array<String> = arrayOf("xml")

    override fun getIcon(): IconRef = XmlIcons.XmlFile
}
