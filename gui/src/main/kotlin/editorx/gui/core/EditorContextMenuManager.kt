package editorx.gui.core

import editorx.core.gui.EditorContextMenuItem
import editorx.core.gui.EditorContextMenuView

/**
 * 编辑器右键菜单项注册表
 * 用于管理插件注册的编辑器右键菜单项，并支持按插件卸载回收。
 */
object EditorContextMenuManager {
    private data class Registration(
        val item: EditorContextMenuItem,
        val ownerId: String?,
    )

    private val registrations: MutableList<Registration> = mutableListOf()

    fun register(item: EditorContextMenuItem) {
        register(item, ownerId = null)
    }

    fun register(item: EditorContextMenuItem, ownerId: String?) {
        registrations.add(Registration(item = item, ownerId = ownerId))
    }

    fun unregisterByOwner(ownerId: String) {
        registrations.removeIf { it.ownerId == ownerId }
    }

    fun getItems(view: EditorContextMenuView): List<EditorContextMenuItem> {
        return registrations.map { it.item }.filter { reg ->
            runCatching { reg.visibleWhen(view) }.getOrDefault(false)
        }
    }
}

