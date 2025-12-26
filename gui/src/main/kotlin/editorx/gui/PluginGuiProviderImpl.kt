package editorx.gui

import editorx.core.filetype.*
import editorx.core.gui.GuiContext
import editorx.core.plugin.gui.FileHandler
import editorx.core.plugin.gui.FileHandlerRegistry
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

    override fun openWorkspace(workspaceDir: File) {
        guiContext.getWorkspace().openWorkspace(workspaceDir)
        mainWindow?.sideBar?.showView("explorer")
        val explorer = mainWindow?.sideBar?.getView("explorer")
        (explorer as? editorx.gui.main.explorer.Explorer)?.refreshRoot()
        mainWindow?.editor?.showEditorContent()
        mainWindow?.titleBar?.updateVcsDisplay()
        mainWindow?.updateNavigation(null)
    }

    override fun openFile(file: File) {
        mainWindow?.editor?.openFile(file)
    }

    override fun showProgress(
        message: String,
        indeterminate: Boolean,
        cancellable: Boolean,
        onCancel: (() -> Unit)?,
        maximum: Int
    ) {
        mainWindow?.statusBar?.showProgress(message, indeterminate, cancellable, onCancel, maximum)
    }

    override fun hideProgress() {
        mainWindow?.statusBar?.hideProgress()
    }

    override fun addToolBarItem(id: String, icon: Icon?, text: String, action: () -> Unit) {
        mainWindow?.toolBar?.addItem(pluginId, id, icon, text, action)
    }

    override fun setToolBarItemEnabled(id: String, enabled: Boolean) {
        mainWindow?.toolBar?.setItemEnabled(id, enabled)
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

    override fun registerFileHandler(handler: FileHandler) {
        FileHandlerRegistry.register(handler)
    }

    override fun getThemeTextColor(): java.awt.Color {
        return ThemeManager.currentTheme.onSurface
    }

    override fun getThemeDisabledTextColor(): java.awt.Color {
        return ThemeManager.currentTheme.onSurfaceVariant
    }
}
