package editorx.gui.core

import editorx.core.filetype.FileType
import editorx.core.filetype.Formatter
import editorx.core.filetype.Language
import editorx.core.filetype.SyntaxHighlighter
import editorx.core.gui.EditorContextMenuItem
import editorx.core.gui.GuiContext
import editorx.core.plugin.FileHandler
import editorx.core.gui.GuiExtension
import editorx.core.util.IconRef
import editorx.gui.MainWindow
import editorx.gui.theme.ThemeManager
import java.io.File

class GuiExtensionImpl(
    private val pluginId: String,
    private val guiContext: GuiContext,
    private val mainWindow: MainWindow?
) : GuiExtension {

    override fun getWorkspaceRoot(): File? {
        return guiContext.getWorkspace().getWorkspaceRoot()
    }

    override fun openWorkspace(workspaceDir: File) {
        guiContext.getWorkspace().openWorkspace(workspaceDir)
        mainWindow?.sideBar?.showView("explorer")
        val explorer = mainWindow?.sideBar?.getView("explorer")
        (explorer as? editorx.gui.workbench.explorer.Explorer)?.refreshRoot()
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

    override fun addToolBarItem(id: String, iconRef: IconRef?, text: String, action: () -> Unit) {
        mainWindow?.toolBar?.addItem(pluginId, id, iconRef, text, action)
    }

    override fun setToolBarItemEnabled(id: String, enabled: Boolean) {
        mainWindow?.toolBar?.setItemEnabled(id, enabled)
    }

    override fun registerFileType(fileType: FileType) {
        FileTypeManager.registerFileType(fileType, ownerId = pluginId)
    }

    override fun unregisterAllFileTypes() {
        FileTypeManager.unregisterByOwner(pluginId)
    }

    override fun registerSyntaxHighlighter(language: Language, syntaxHighlighter: SyntaxHighlighter) {
        SyntaxHighlighterManager.registerSyntaxHighlighter(
            language,
            syntaxHighlighter,
            ownerId = pluginId
        )
    }

    override fun unregisterAllSyntaxHighlighters() {
        SyntaxHighlighterManager.unregisterByOwner(pluginId)
    }

    override fun registerFormatter(language: Language, formatter: Formatter) {
        FormatterManager.registerFormatter(language, formatter, ownerId = pluginId)
    }

    override fun unregisterAllFormatters() {
        FormatterManager.unregisterByOwner(pluginId)
    }

    override fun registerFileHandler(handler: FileHandler) {
        FileHandlerManager.register(handler, ownerId = pluginId)
    }

    override fun unregisterAllFileHandlers() {
        FileHandlerManager.unregisterByOwner(pluginId)
    }

    override fun registerEditorContextMenuItem(item: EditorContextMenuItem) {
        EditorContextMenuManager.register(item, ownerId = pluginId)
    }

    override fun unregisterAllEditorContextMenuItems() {
        EditorContextMenuManager.unregisterByOwner(pluginId)
    }

    override fun getThemeTextColor(): java.awt.Color {
        return ThemeManager.currentTheme.onSurface
    }

    override fun getThemeDisabledTextColor(): java.awt.Color {
        return ThemeManager.currentTheme.onSurfaceVariant
    }
}
