package editorx.workspace

import editorx.settings.SettingsStore
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

class DefaultWorkspaceManager(private val settings: SettingsStore) : WorkspaceManager {
    private var root: File? = null

    override fun getWorkspaceRoot(): File? = root

    override fun openWorkspace(root: File) {
        this.root = root
    }

    override fun recentFiles(): List<File> {
        val keys = settings.keys("files.recent.").sorted()
        return keys.mapNotNull { settings.get(it, null) }.map(::File).filter { it.exists() }
    }

    override fun addRecentFile(file: File) {
        val existing = recentFiles().toMutableList()
        existing.remove(file)
        existing.add(0, file)
        val top = existing.take(10)
        // Clear old keys
        settings.keys("files.recent.").forEach { settings.remove(it) }
        top.forEachIndexed { idx, f -> settings.put("files.recent.$idx", f.absolutePath) }
        settings.sync()
    }
}