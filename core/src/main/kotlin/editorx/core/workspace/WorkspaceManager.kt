package editorx.core.workspace

import editorx.core.store.Store
import java.io.File

/**
 * Workspace model for EditorX (path can be a folder or virtual workspace).
 */
interface WorkspaceManager {
    fun getWorkspaceRoot(): File?
    fun openWorkspace(root: File)
    fun recentFiles(): List<File>
    fun addRecentFile(file: File)
    fun recentWorkspaces(): List<File>
    fun addRecentWorkspace(workspace: File)
}

class DefaultWorkspaceManager(private val settings: Store) : editorx.core.workspace.WorkspaceManager {
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

    override fun recentWorkspaces(): List<File> {
        val keys = settings.keys("workspaces.recent.").sorted()
        val workspaces = keys.mapNotNull { key ->
            settings.get(key, null)?.let { path ->
                try {
                    File(path)
                } catch (e: Exception) {
                    null
                }
            }
        }.filter { it.exists() && it.isDirectory }
        return workspaces
    }

    override fun addRecentWorkspace(workspace: File) {
        if (!workspace.exists() || !workspace.isDirectory) {
            return // 只保存存在的目录
        }
        val workspacePath = workspace.absolutePath
        val existing = recentWorkspaces().toMutableList()
        // 使用绝对路径比较，移除重复项
        existing.removeAll { it.absolutePath == workspacePath }
        existing.add(0, workspace)
        val top = existing.take(20) // 保留最近20个项目
        // Clear old keys
        settings.keys("workspaces.recent.").forEach { settings.remove(it) }
        top.forEachIndexed { idx, f -> settings.put("workspaces.recent.$idx", f.absolutePath) }
        settings.sync()
    }
}