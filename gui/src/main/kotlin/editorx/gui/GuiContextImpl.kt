package editorx.gui

import editorx.core.gui.GuiContext
import editorx.core.plugin.PluginManager
import editorx.core.store.Store
import editorx.core.workspace.Workspace
import java.io.File

/**
 * GUI 上下文实现类
 */
class GuiContextImpl(
    private val appDir: File,
    private val settings: Store,
    private val workspace: Workspace,
    private val pluginManager: PluginManager
) : GuiContext {

    override fun getAppDir(): File = appDir

    override fun getSettings(): Store = settings

    override fun getWorkspace(): Workspace = workspace

    override fun getPluginManager(): PluginManager = pluginManager
}

