package editorx.gui.plugin

import editorx.core.filetype.FileType
import editorx.core.filetype.FileTypeRegistry
import editorx.core.filetype.Formatter
import editorx.core.filetype.FormatterRegistry
import editorx.core.filetype.SyntaxHighlighter
import editorx.core.filetype.SyntaxHighlighterRegistry
import editorx.core.filetype.Language
import editorx.core.gui.GuiContext
import editorx.core.plugin.gui.PluginGuiClient

class PluginGuiContextImpl(
    private val pluginId: String,
    private val guiContext: GuiContext
) : PluginGuiClient {

    override fun getWorkspaceRoot(): java.io.File? {
        return guiContext.workspace.getWorkspaceRoot()
    }

    override fun registerFileType(fileType: FileType) {
        FileTypeRegistry.registerFileType(fileType, ownerId = pluginId)
    }

    override fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter) {
        SyntaxHighlighterRegistry.registerSyntaxHighlighter(
            language,
            syntaxHighlighter,
            ownerId = pluginId
        )
    }

    override fun registerFormatter(language: Language, formatter: Formatter) {
        FormatterRegistry.registerFormatter(language, formatter, ownerId = pluginId)
    }
}
