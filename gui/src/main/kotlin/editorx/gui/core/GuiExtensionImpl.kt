package editorx.gui.core

import editorx.core.filetype.FileType
import editorx.core.filetype.Formatter
import editorx.core.filetype.Language
import editorx.core.filetype.SyntaxHighlighter
import editorx.core.gui.DiffHunk
import editorx.core.gui.EditorMenuItem
import editorx.core.gui.GuiContext
import editorx.core.gui.GuiExtension
import editorx.core.gui.GuiViewProvider
import editorx.core.plugin.FileHandler
import editorx.core.util.IconRef
import editorx.gui.MainWindow
import editorx.gui.theme.ThemeManager
import editorx.gui.workbench.explorer.Explorer
import java.awt.Component
import java.io.File
import javax.swing.SwingUtilities
import javax.swing.Timer

class GuiExtensionImpl(
    private val pluginId: String,
    private val guiContext: GuiContext,
    private val mainWindow: MainWindow?
) : GuiExtension {

    private var progressHandle: editorx.gui.workbench.statusbar.StatusBar.ProgressHandle? = null

    override fun getWorkspaceRoot(): File? {
        return guiContext.getWorkspace().getWorkspaceRoot()
    }

    override fun showFileChooser(callback: (File?) -> Unit) {
        val window = mainWindow
        if (window == null) {
            callback.invoke(null)
            return
        }
        window.showFileChooser(callback)
    }

    override fun openWorkspace(workspaceDir: File) {
        guiContext.getWorkspace().openWorkspace(workspaceDir)
        // 通过 ActivityBar 显示 Explorer（会自动更新高亮状态）
        mainWindow?.activityBar?.activateItem("explorer")
        mainWindow?.editor?.showEditorContent()
        mainWindow?.titleBar?.updateVcsDisplay()
        mainWindow?.updateNavigation(null)

        // 刷新 Explorer
        val explorer = mainWindow?.sideBar?.getView("explorer")
        val explorerInstance = explorer as? Explorer
        explorerInstance?.refreshRoot()

        // 检查并创建 Git 仓库（延迟执行，避免阻塞）
        javax.swing.SwingUtilities.invokeLater {
            explorerInstance?.checkAndPromptCreateGitRepository(workspaceDir)
        }
    }

    override fun openFile(file: File) {
        mainWindow?.editor?.openFile(file)
    }

    override fun openEditorTab(id: String, title: String, component: Component, iconRef: IconRef?) {
        mainWindow?.editor?.openCustomTab(pluginId, id, title, iconRef, component)
    }

    override fun openDiffTab(
        id: String,
        title: String,
        file: File?,
        leftTitle: String,
        leftText: String,
        rightTitle: String,
        rightText: String,
        hunks: List<DiffHunk>
    ) {
        mainWindow?.editor?.openDiffTab(
            ownerId = pluginId,
            tabId = id,
            title = title,
            file = file,
            leftTitle = leftTitle,
            leftText = leftText,
            rightTitle = rightTitle,
            rightText = rightText,
            hunks = hunks
        )
    }

    override fun showProgress(
        message: String,
        indeterminate: Boolean,
        cancellable: Boolean,
        onCancel: (() -> Unit)?,
        maximum: Int
    ) {
        val bar = mainWindow?.statusBar ?: return
        val handle = progressHandle ?: bar.beginProgressTask(
            message = message,
            indeterminate = indeterminate,
            cancellable = cancellable,
            onCancel = onCancel,
            maximum = maximum
        ).also { progressHandle = it }
        bar.updateProgressTask(
            handle = handle,
            message = message,
            indeterminate = indeterminate,
            cancellable = cancellable,
            onCancel = onCancel,
            maximum = maximum
        )
    }

    override fun hideProgress() {
        val bar = mainWindow?.statusBar ?: return
        val handle = progressHandle ?: return
        progressHandle = null
        bar.endProgressTask(handle)
    }

    override fun refreshExplorer(preserveSelection: Boolean) {
        val explorer = mainWindow?.sideBar?.getView("explorer") as? Explorer ?: return
        if (preserveSelection) explorer.refreshRootPreserveSelection() else explorer.refreshRoot()
    }

    override fun revealInExplorer(file: File) {
        val window = mainWindow ?: return
        SwingUtilities.invokeLater {
            window.activityBar.activateItem("explorer")
            val explorer = window.sideBar.getView("explorer") as? Explorer ?: return@invokeLater

            // 触发一次刷新，确保新生成的文件能被树模型加载到（避免目录已 load 但未 reload 导致找不到新文件）。
            explorer.refreshRootPreserveSelection()

            // refreshRootPreserveSelection 是异步的，这里用短轮询在刷新完成后再定位文件。
            var attempts = 0
            val timer = Timer(150, null)
            timer.addActionListener {
                attempts++
                explorer.focusFileInTree(file)
                if (attempts >= 10) timer.stop()
            }
            timer.initialDelay = 0
            timer.start()
        }
    }

    override fun addToolBarItem(id: String, iconRef: IconRef?, text: String, action: () -> Unit) {
        mainWindow?.toolBar?.addItem(pluginId, id, iconRef, text, action)
    }

    override fun addActivityBarItem(id: String, iconRef: IconRef?, tooltip: String, viewProvider: GuiViewProvider) {
        mainWindow?.activityBar?.addItem(pluginId, id, tooltip, iconRef, viewProvider)
    }

    override fun setToolBarItemEnabled(id: String, enabled: Boolean) {
        mainWindow?.toolBar?.setItemEnabled(id, enabled)
    }

    override fun unregisterAllToolBarItems() {
        mainWindow?.toolBar?.removeItems(pluginId)
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

    override fun registerEditorMenuItem(item: EditorMenuItem) {
        EditorContextMenuManager.register(item, ownerId = pluginId)
    }

    override fun unregisterAllEditorMenuItems() {
        EditorContextMenuManager.unregisterByOwner(pluginId)
    }

    override fun getThemeTextColor(): java.awt.Color {
        return ThemeManager.currentTheme.onSurface
    }

    override fun getThemeDisabledTextColor(): java.awt.Color {
        return ThemeManager.currentTheme.onSurfaceVariant
    }
}
