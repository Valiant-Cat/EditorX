package editorx.plugins.androidarchive

import editorx.core.filetype.FileType
import javax.swing.Icon

object AndroidArchiveFileType : FileType {
    override fun getName(): String = "android-archive"

    override fun getDisplayName(): String = "Android Archive"

    override fun getExtensions(): Array<String> = arrayOf("xapk", "aab", "aar")

    override fun getIcon(): Icon? = AndroidArchiveIcons.ArchiveFile
}

