package editorx.gui.services

import editorx.command.CommandMeta
import editorx.command.CommandRegistry
import editorx.command.DefaultCommandRegistry
import editorx.event.EventBus
import editorx.event.SimpleEventBus
import editorx.settings.PropertiesSettingsStore
import editorx.settings.SettingsStore
import editorx.workspace.WorkspaceManager
import java.io.File

class GuiServices(private val appDir: File) {
    val eventBus: EventBus = SimpleEventBus()
    val commands: CommandRegistry = DefaultCommandRegistry()
    val settings: SettingsStore by lazy {
        val settingsFile = File(appDir, "settings.properties")
        PropertiesSettingsStore(settingsFile)
    }
    val workspace: WorkspaceManager = DefaultWorkspaceManager(settings)

    init {
        registerBuiltinCommands()
    }

    private fun registerBuiltinCommands() {
        val registry = commands
        val builtins = listOf(
            CommandMeta("app.about", "关于 EditorX"),
            CommandMeta("view.toggleSidebar", "切换侧边栏"),
            CommandMeta("view.togglePanel", "切换底部面板"),
            CommandMeta("file.open", "打开文件"),
            CommandMeta("file.save", "保存文件"),
            CommandMeta("file.saveAs", "另存为..."),
            CommandMeta("help.commandPalette", "打开命令面板")
        )
        // Handlers are bound in GUI layer where components exist
        builtins.forEach { meta -> if (!registry.has(meta.id)) registry.register(meta) { } }
    }
}

private class DefaultWorkspaceManager(private val settings: SettingsStore) : WorkspaceManager {
    private var root: File? = null

    override fun getWorkspaceRoot(): File? = root

    override fun openWorkspace(root: File) {
        this.root = root
    }

    override fun recentFiles(): List<File> {
        val keys = settings.keys("files.recent.").sorted()
        return keys.mapNotNull { settings.get(it, null) }.map(::File).filter { it.exists() }
    }

    override fun addRecentFile(file: File) {
        val existing = recentFiles().toMutableList()
        existing.remove(file)
        existing.add(0, file)
        val top = existing.take(10)
        // Clear old keys
        settings.keys("files.recent.").forEach { settings.remove(it) }
        top.forEachIndexed { idx, f -> settings.put("files.recent.$idx", f.absolutePath) }
        settings.sync()
    }
}

