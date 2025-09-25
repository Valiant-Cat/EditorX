package editorx.workspace

import java.io.File

/**
 * Workspace model for EditorX (path can be a folder or virtual workspace).
 */
interface WorkspaceManager {
    fun getWorkspaceRoot(): File?
    fun openWorkspace(root: File)
    fun recentFiles(): List<File>
    fun addRecentFile(file: File)
}

