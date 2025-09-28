package editorx.file

import editorx.gui.IconRef


/**
 * FileType describes a category of files (by name/extension pattern) and presentation.
 */
interface FileType {

    /**
     * Returns the name of the file type. The name must be unique among all file types registered in the system.
     */
    fun getName(): String

    fun getDisplayName(): String {
        return getName()
    }

    /**
     * Returns the user-readable description of the file type.
     */
    fun getDescription(): String

    /**
     * Returns the default extension for files of the type, <em>not</em> including the leading '.'.
     */
    fun getExtensions(): Set<String>

    /** Optional icon for Explorer or tabs. */
    fun getIcon(): IconRef

    /**
     * Returns `true` if files of the specified type contain binary data, `false` if the file is plain text.
     * Used for source control, to-do items scanning and other purposes.
     */
    fun isBinary(): Boolean

    /**
     * Returns `true` if the specified file type is read-only. Read-only file types are not shown in the "File Types" settings dialog,
     * and users cannot change the extensions associated with the file type.
     */
    fun isReadOnly(): Boolean {
        return false
    }
}

