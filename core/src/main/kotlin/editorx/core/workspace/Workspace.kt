package editorx.core.workspace

import java.io.File

/**
 * Workspace model for EditorX (path can be a folder or virtual workspace).
 */
interface Workspace {
    fun getWorkspaceRoot(): File?
    fun openWorkspace(root: File)
    fun recentFiles(): List<File>
    fun addRecentFile(file: File)
    fun recentWorkspaces(): List<File>
    fun addRecentWorkspace(workspace: File)
}