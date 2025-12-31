package editorx.core.gui

import editorx.core.filetype.FileType
import editorx.core.filetype.Formatter
import editorx.core.filetype.Language
import editorx.core.filetype.SyntaxHighlighter
import editorx.core.plugin.FileHandler
import editorx.core.util.IconRef
import java.awt.Color
import java.io.File

interface GuiExtension {

    /**
     * 获取工作区根目录
     * 返回当前打开的工作区根目录，如果未打开工作区则返回 null
     */
    fun getWorkspaceRoot(): File?

    /**
     * 打开工作区
     * @param workspaceDir 工作区目录
     */
    fun openWorkspace(workspaceDir: File)

    /**
     * 打开文件
     * @param file 要打开的文件
     */
    fun openFile(file: File)

    /**
     * 显示进度
     * @param message 进度消息
     */
    fun showProgress(
        message: String,
        indeterminate: Boolean = true,
        cancellable: Boolean = false,
        onCancel: (() -> Unit)? = null,
        maximum: Int = 100
    )

    /**
     * 隐藏进度
     */
    fun hideProgress()

    /**
     * 在 ToolBar 添加一个快捷按钮
     * @param id 按钮的唯一标识符
     * @param iconRef 图标引用（图标尺寸由 ToolBar 统一设置）
     * @param text 按钮文本
     * @param action 按钮点击时的动作
     */
    fun addToolBarItem(id: String, iconRef: IconRef?, text: String, action: () -> Unit)

    /**
     * 设置 ToolBar 按钮的启用/禁用状态
     * @param id 按钮的唯一标识符
     * @param enabled 是否启用
     */
    fun setToolBarItemEnabled(id: String, enabled: Boolean)

    /**
     * 注册文件类型
     */
    fun registerFileType(fileType: FileType)

    /**
     * 取消注册所有文件类型（按插件 ID）
     * 插件应在 deactivate 时调用此方法
     */
    fun unregisterAllFileTypes()

    /**
     * 注册语法高亮
     */
    fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter)

    /**
     * 取消注册所有语法高亮（按插件 ID）
     * 插件应在 deactivate 时调用此方法
     */
    fun unregisterAllSyntaxHighlighters()

    /**
     * 注册格式化器
     */
    fun registerFormatter(language: Language, formatter: Formatter)

    /**
     * 取消注册所有格式化器（按插件 ID）
     * 插件应在 deactivate 时调用此方法
     */
    fun unregisterAllFormatters()

    /**
     * 注册文件处理器
     * @param handler 文件处理器
     */
    fun registerFileHandler(handler: FileHandler)

    /**
     * 取消注册所有文件处理器（按插件 ID）
     * 插件应在 deactivate 时调用此方法
     */
    fun unregisterAllFileHandlers()

    /**
     * 注册编辑器右键菜单项
     */
    fun registerEditorContextMenuItem(item: EditorContextMenuItem)

    /**
     * 取消注册所有编辑器右键菜单项（按插件 ID）
     * 插件应在 deactivate 时调用此方法
     */
    fun unregisterAllEditorContextMenuItems()

    /**
     * 获取当前主题的文本颜色（用于图标等）
     * @return 当前主题的文本颜色
     */
    fun getThemeTextColor(): Color

    /**
     * 获取当前主题的禁用状态文本颜色（用于禁用状态的图标等）
     * @return 当前主题的禁用状态文本颜色
     */
    fun getThemeDisabledTextColor(): Color
}
