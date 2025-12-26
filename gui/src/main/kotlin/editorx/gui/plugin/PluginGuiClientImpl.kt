package editorx.gui.plugin

import editorx.core.filetype.*
import editorx.core.gui.GuiContext
import editorx.core.plugin.gui.PluginGuiClient

class PluginGuiClientImpl(private val pluginId: String, private val guiContext: GuiContext) : PluginGuiClient {

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
