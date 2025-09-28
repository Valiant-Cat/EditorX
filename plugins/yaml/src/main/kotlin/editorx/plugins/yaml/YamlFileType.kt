import editorx.gui.IconRef
import editorx.lang.LanguageFileType
import editorx.plugins.yaml.YamlIcons

class YamlFileType : LanguageFileType(YamlLanguage.getInstance()) {
    override fun getName(): String = "yaml"

    override fun getDescription(): String = "YAML files"

    override fun getExtensions(): Set<String> = setOf("yaml", "yml")

    override fun getIcon(): IconRef = YamlIcons.YamlFile
}
