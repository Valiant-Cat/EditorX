package editorx.plugins.androidarchive

import editorx.core.util.IconRef
import editorx.core.util.IconLoader
import javax.swing.Icon

object AndroidArchiveIcons {
    val ArchiveFile: Icon? = load("icons/android-archive.svg")

    private fun load(path: String): Icon? {
        return IconLoader.getIcon(IconRef(path, AndroidArchiveIcons::class.java.classLoader))
    }
}

