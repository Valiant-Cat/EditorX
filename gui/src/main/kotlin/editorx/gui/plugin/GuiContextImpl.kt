package editorx.gui.plugin

import editorx.core.filetype.FileType
import editorx.core.filetype.FileTypeRegistry
import editorx.core.filetype.SyntaxHighlighter
import editorx.core.filetype.SyntaxHighlighterRegistry
import editorx.core.lang.Language
import editorx.core.plugin.PluginContext
import editorx.core.plugin.gui.GuiContext
import editorx.gui.main.MainWindow

class GuiContextImpl(
    private val mainWindow: MainWindow,
    private val pluginContext: PluginContext
) : GuiContext {

    override fun getWorkspaceRoot(): java.io.File? {
        return mainWindow.guiControl.workspace.getWorkspaceRoot()
    }

    override fun registerFileType(fileType: FileType) {
        FileTypeRegistry.registerFileType(fileType, ownerId = pluginContext.pluginId())
    }

    override fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter) {
        SyntaxHighlighterRegistry.registerSyntaxHighlighter(language, syntaxHighlighter, ownerId = pluginContext.pluginId())
    }
}
