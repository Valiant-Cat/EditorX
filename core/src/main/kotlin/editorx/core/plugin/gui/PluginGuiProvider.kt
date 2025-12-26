package editorx.core.plugin.gui

import editorx.core.filetype.FileType
import editorx.core.filetype.Formatter
import editorx.core.filetype.Language
import editorx.core.filetype.SyntaxHighlighter
import java.io.File
import javax.swing.Icon

interface PluginGuiProvider {

    /**
     * 获取工作区根目录
     * 返回当前打开的工作区根目录，如果未打开工作区则返回 null
     */
    fun getWorkspaceRoot(): File?

    /**
     * 打开文件
     * @param file 要打开的文件
     */
    fun openFile(file: File)

    /**
     * 注册文件类型
     */
    fun registerFileType(fileType: FileType)

    /**
     * 注册语法高亮
     */
    fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter)

    /**
     * 注册格式化器
     */
    fun registerFormatter(language: Language, formatter: Formatter)

    /**
     * 在 ToolBar 添加一个快捷按钮
     * @param id 按钮的唯一标识符
     * @param icon 按钮图标
     * @param text 按钮文本
     * @param action 按钮点击时的动作
     */
    fun addToolBarItem(id: String, icon: Icon?, text: String, action: () -> Unit)
}
