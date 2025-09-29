package editorx.filetype

import editorx.gui.IconRef

interface FileType {

    fun getName(): String

    fun getDisplayName(): String {
        return getName()
    }

    fun getExtensions(): Array<String>

    fun getIcon(): IconRef
}

