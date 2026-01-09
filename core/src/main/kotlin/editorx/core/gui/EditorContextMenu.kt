package editorx.core.gui

import java.io.File

/**
 * 编辑器右键菜单的只读视图上下文
 */
data class EditorMenuView(
    val file: File?,
    val languageId: String?,
    val editable: Boolean,
    val selectionStart: Int,
    val selectionEnd: Int,
) {
    val hasSelection: Boolean get() = selectionStart != selectionEnd
}

/**
 * 编辑器右键菜单动作执行上下文
 * 由 GUI 实现提供，用于读取/修改当前编辑器内容。
 */
interface EditorMenuHandler {
    val view: EditorMenuView

    fun getText(): String

    /**
     * 替换全文内容（由 GUI 负责尽量保持撤销历史与脏标记一致性）
     */
    fun replaceText(newText: String)

    fun getSelectedText(): String?

    /**
     * 替换当前选中内容
     */
    fun replaceSelectedText(newText: String)
}

/**
 * 编辑器右键菜单项
 *
 * @param id 菜单项唯一标识（建议使用插件前缀，如 "stringfog.decrypt"）
 * @param text 菜单展示文本
 * @param visibleWhen 控制是否显示
 * @param enabledWhen 控制是否可用
 * @param action 点击后的动作
 */
data class EditorMenuItem(
    val id: String,
    val text: String,
    val visibleWhen: (EditorMenuView) -> Boolean = { true },
    val enabledWhen: (EditorMenuView) -> Boolean = { true },
    val action: (EditorMenuHandler) -> Unit,
)

