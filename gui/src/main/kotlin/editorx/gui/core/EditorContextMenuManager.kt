package editorx.gui.core

import editorx.core.gui.EditorMenuItem
import editorx.core.gui.EditorMenuView

/**
 * 编辑器右键菜单项注册表
 * 用于管理插件注册的编辑器右键菜单项，并支持按插件卸载回收。
 */
object EditorContextMenuManager {
    private data class Registration(
        val item: EditorMenuItem,
        val ownerId: String?,
    )

    private val registrations: MutableList<Registration> = mutableListOf()

    fun register(item: EditorMenuItem) {
        register(item, ownerId = null)
    }

    fun register(item: EditorMenuItem, ownerId: String?) {
        registrations.add(Registration(item = item, ownerId = ownerId))
    }

    fun unregisterByOwner(ownerId: String) {
        registrations.removeIf { it.ownerId == ownerId }
    }

    fun getItems(view: EditorMenuView): List<EditorMenuItem> {
        return registrations.map { it.item }.filter { reg ->
            runCatching { reg.visibleWhen(view) }.getOrDefault(false)
        }
    }
}

