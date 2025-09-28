package editorx.plugins.smali

import editorx.gui.IconRef

object SmaliIcons {
    val SmaliFile: IconRef = load("icons/smali.svg")

    private fun load(path: String): IconRef {
        return IconRef(path, SmaliIcons::class.java.classLoader)
    }
}