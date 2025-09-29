import editorx.filetype.LanguageFileType
import editorx.gui.IconRef
import editorx.plugins.yaml.YamlIcons

object YamlFileType : LanguageFileType(YamlLanguage) {
    override fun getName(): String = "yaml"

    override fun getExtensions(): Array<String> = arrayOf("yml", "yaml")

    override fun getIcon(): IconRef = YamlIcons.YamlFile
}
