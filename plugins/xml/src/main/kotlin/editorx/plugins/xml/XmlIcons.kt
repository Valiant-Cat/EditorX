package editorx.plugins.xml

import editorx.gui.IconRef

object XmlIcons {
    val XmlFile: IconRef = load("icons/xml.svg")

    private fun load(path: String): IconRef {
        return IconRef(path, XmlIcons::class.java.classLoader)
    }
}