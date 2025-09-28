package editorx.plugins.json

import editorx.gui.IconRef

object JsonIcons {
    val JsonFile: IconRef = load("icons/json.svg")

    private fun load(path: String): IconRef {
        return IconRef(path, JsonIcons::class.java.classLoader)
    }
}