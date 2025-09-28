package editorx.plugins.yaml

import editorx.gui.IconRef

object YamlIcons {
    val YamlFile: IconRef = load("icons/yaml.svg")

    private fun load(path: String): IconRef {
        return IconRef(path, YamlIcons::class.java.classLoader)
    }
}