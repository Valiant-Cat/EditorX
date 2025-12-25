package editorx.gui.main.explorer

import editorx.core.util.IconRef
import editorx.core.util.IconLoader
import javax.swing.Icon

object ExplorerIcons {
    val Folder: Icon? = load("icons/common/folder.svg")
    val AnyType: Icon? = load("icons/common/anyType.svg")
    val ResourcesRoot: Icon? = load("icons/gui/resourcesRoot.svg")
    val SourceRoot: Icon? = load("icons/gui/sourceRoot.svg")

    private fun load(path: String): Icon? {
        return IconLoader.getIcon(IconRef(path, ExplorerIcons::class.java.classLoader))
    }
}