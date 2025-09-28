import editorx.gui.IconRef
import editorx.lang.LanguageFileType
import editorx.plugins.xml.XmlIcons

class XmlFileType : LanguageFileType(XmlLanguage.getInstance()) {
    override fun getName(): String = "xml"

    override fun getDescription(): String = "XML files"

    override fun getExtensions(): Set<String> = setOf("xml")

    override fun getIcon(): IconRef = XmlIcons.XmlFile
}
