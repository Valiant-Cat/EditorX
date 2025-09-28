package editorx.gui.main.explorer

import editorx.gui.IconRef
import editorx.util.IconLoader
import javax.swing.Icon

object ExplorerIcons {
    val Folder: Icon? = load("icons/folder.svg")
    val AnyType: Icon? = load("icons/anyType.svg")

    private fun load(path: String): Icon? {
        return IconLoader.getIcon(IconRef(path, ExplorerIcons::class.java.classLoader))
    }
}