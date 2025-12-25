package editorx.core.plugin.gui

import editorx.core.filetype.FileType
import editorx.core.filetype.SyntaxHighlighter
import editorx.core.gui.GuiViewProvider
import editorx.core.lang.Language
import java.io.File

interface GuiContext {

    /**
     * 获取工作区根目录
     * 返回当前打开的工作区根目录，如果未打开工作区则返回 null
     */
    fun getWorkspaceRoot(): File?

    /**
     * 注册文件类型
     */
    fun registerFileType(fileType: FileType)

    /**
     * 注册语法高亮
     */
    fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter)
}
