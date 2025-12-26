package editorx.gui

import editorx.core.filetype.*
import editorx.core.gui.GuiContext
import editorx.core.plugin.gui.PluginGuiProvider
import java.io.File
import javax.swing.Icon

class PluginGuiProviderImpl(
    private val pluginId: String,
    private val guiContext: GuiContext,
    private val mainWindow: editorx.gui.main.MainWindow?
) : PluginGuiProvider {

    override fun getWorkspaceRoot(): File? {
        return guiContext.getWorkspace().getWorkspaceRoot()
    }

    override fun openFile(file: File) {
        mainWindow?.editor?.openFile(file)
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

    override fun addToolBarItem(id: String, icon: Icon?, text: String, action: () -> Unit) {
        mainWindow?.toolBar?.addItem(pluginId, id, icon, text, action)
    }
}
