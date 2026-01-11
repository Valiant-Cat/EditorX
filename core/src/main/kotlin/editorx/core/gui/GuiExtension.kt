package editorx.core.gui

import editorx.core.filetype.FileType
import editorx.core.filetype.Formatter
import editorx.core.filetype.Language
import editorx.core.filetype.SyntaxHighlighter
import editorx.core.plugin.FileHandler
import editorx.core.util.IconRef
import java.awt.Color
import java.awt.Component
import java.io.File

interface GuiExtension {

    /**
     * 获取工作区根目录
     * 返回当前打开的工作区根目录，如果未打开工作区则返回 null
     */
    fun getWorkspaceRoot(): File?

    /**
     * 显示文件选择对话框
     * 返回选中的文件，如果用户取消则返回 null
     */
    fun showFileChooser(callback: (File?) -> Unit)

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
     * 在编辑器中打开一个自定义标签页（例如 Diff 视图、预览面板等）。
     *
     * 注意：id 应在同一插件内保持稳定，便于重复打开时复用/刷新同一标签页。
     *
     * @param id 标签页唯一标识（建议在插件内稳定）
     * @param title 标签页标题
     * @param component 要显示的 UI 组件
     * @param iconRef 标签页图标（可选）
     */
    fun openEditorTab(id: String, title: String, component: Component, iconRef: IconRef? = null)

    /**
     * 在编辑器中打开一个 Diff 标签页（左右对比视图）。
     *
     * @param id 标签页唯一标识（建议在插件内稳定）
     * @param title 标签页标题
     * @param file 用于语法高亮检测的文件（可选）
     * @param leftTitle 左侧标题（例如：HEAD/暂存区）
     * @param leftText 左侧内容
     * @param rightTitle 右侧标题（例如：工作区/暂存区）
     * @param rightText 右侧内容
     * @param hunks 变更片段列表（用于高亮变更行）
     */
    fun openDiffTab(
        id: String,
        title: String,
        file: File?,
        leftTitle: String,
        leftText: String,
        rightTitle: String,
        rightText: String,
        hunks: List<DiffHunk> = emptyList(),
    )

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
     * 刷新文件树（Explorer）。
     *
     * 说明：
     * - 用于构建/生成文件后让新产物即时出现在文件树中。
     * - 默认尽量保留当前选择与展开状态。
     */
    fun refreshExplorer(preserveSelection: Boolean = true)

    /**
     * 在 Explorer 中定位并选中指定文件（必要时会自动切换到 Explorer 视图）。
     *
     * 说明：
     * - 主要用于构建产物生成后，点击“定位”快速在文件树中选中该文件。
     */
    fun revealInExplorer(file: File)

    /**
     * 在 ToolBar 添加一个快捷按钮
     * @param id 按钮的唯一标识符
     * @param iconRef 图标引用（图标尺寸由 ToolBar 统一设置）
     * @param text 按钮文本
     * @param action 按钮点击时的动作
     */
    fun addToolBarItem(id: String, iconRef: IconRef?, text: String, action: () -> Unit)

    /**
     * 在 ActivityBar 添加一个入口（侧边栏视图）
     * @param id 入口的唯一标识符（建议全局唯一）
     * @param iconRef 图标引用（图标尺寸由 ActivityBar 统一设置）
     * @param tooltip 提示文本
     * @param viewProvider 视图提供器
     */
    fun addActivityBarItem(id: String, iconRef: IconRef?, tooltip: String, viewProvider: GuiViewProvider)

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
    fun registerEditorMenuItem(item: EditorMenuItem)

    /**
     * 取消注册所有编辑器右键菜单项（按插件 ID）
     * 插件应在 deactivate 时调用此方法
     */
    fun unregisterAllEditorMenuItems()

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
