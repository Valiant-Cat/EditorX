package editorx.gui.workbench.explorer

import editorx.core.util.IconRef
import editorx.core.util.IconLoader
import javax.swing.Icon

object ExplorerIcons {
    val Folder: Icon? = load("icons/filetype/folder.svg")
    val AnyType: Icon? = load("icons/filetype/anyType.svg")
    val ResourcesRoot: Icon? = load("icons/filetype/resourcesRoot.svg")
    val SourceRoot: Icon? = load("icons/filetype/module.svg")

    private fun load(path: String): Icon? {
        return IconLoader.getIcon(IconRef(path, ExplorerIcons::class.java.classLoader))
    }
}