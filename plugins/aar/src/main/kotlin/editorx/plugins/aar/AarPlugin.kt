package editorx.plugins.aar

import editorx.core.gui.GuiExtension
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo
import editorx.core.service.BuildService
import editorx.core.util.IconRef
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import javax.swing.JOptionPane
import javax.swing.Timer

class AarPlugin : Plugin {
    companion object {
        private val logger = LoggerFactory.getLogger(AarPlugin::class.java)
    }

    override fun getInfo() = PluginInfo(
        id = "aar",
        name = "AAR",
        version = "0.0.1",
    )

    private var workspaceCheckTimer: Timer? = null
    private var lastWorkspaceState: Boolean = false
    private var toolbarRegistered: Boolean = false
    private var pluginContext: PluginContext? = null
    private var buildService: AarBuildService? = null

    override fun activate(pluginContext: PluginContext) {
        this.pluginContext = pluginContext

        buildService = AarBuildService()
        pluginContext.registerService(BuildService::class.java, buildService!!)

        val gui = pluginContext.gui() ?: return

        gui.registerFileHandler(AarFileHandler(gui))

        refreshWorkspaceState(gui)
        startWorkspaceStateChecker(gui)
    }

    override fun deactivate() {
        buildService?.let { provider ->
            pluginContext?.unregisterService(BuildService::class.java, provider)
        }
        buildService = null

        pluginContext?.gui()?.unregisterAllFileHandlers()
        pluginContext?.gui()?.unregisterAllFileTypes()
        pluginContext?.gui()?.unregisterAllToolBarItems()
        toolbarRegistered = false

        pluginContext = null

        workspaceCheckTimer?.stop()
        workspaceCheckTimer = null
    }

    private fun startWorkspaceStateChecker(gui: GuiExtension) {
        workspaceCheckTimer = Timer(500) {
            val isAarWorkspace = isAarWorkspace(gui.getWorkspaceRoot())
            if (isAarWorkspace != lastWorkspaceState) {
                lastWorkspaceState = isAarWorkspace
                refreshWorkspaceState(gui)
            }
        }
        workspaceCheckTimer?.isRepeats = true
        workspaceCheckTimer?.start()
    }

    private fun updateToolBarButtonsState(gui: GuiExtension, enabled: Boolean) {
        if (!toolbarRegistered) return
        gui.setToolBarItemEnabled("aar.manifest", enabled)
    }

    private fun refreshWorkspaceState(gui: GuiExtension) {
        val isAarWorkspace = isAarWorkspace(gui.getWorkspaceRoot())
        if (isAarWorkspace && !toolbarRegistered) {
            registerToolBarItems(gui)
            toolbarRegistered = true
        } else if (!isAarWorkspace && toolbarRegistered) {
            gui.unregisterAllToolBarItems()
            toolbarRegistered = false
        }
        updateToolBarButtonsState(gui, isAarWorkspace)
    }

    private fun registerToolBarItems(gui: GuiExtension) {
        gui.addToolBarItem(
            id = "aar.manifest",
            iconRef = IconRef("icons/android-manifest.svg", AarPlugin::class.java.classLoader),
            text = I18n.translate(I18nKeys.Toolbar.GOTO_MANIFEST),
            action = {
                navigateToAndroidManifest(gui)
            }
        )
    }

    private fun isAarWorkspace(workspaceRoot: File?): Boolean {
        if (workspaceRoot == null) return false
        return AarWorkspaceMarker.isAarWorkspace(workspaceRoot)
    }

    private fun navigateToAndroidManifest(gui: GuiExtension) {
        val workspaceRoot = gui.getWorkspaceRoot()
        if (workspaceRoot == null) {
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.WORKSPACE_NOT_OPENED),
                I18n.translate(I18nKeys.Dialog.TIP),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val manifestFile = File(workspaceRoot, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            showMessage(
                I18n.translate(I18nKeys.ToolbarMessage.MANIFEST_NOT_FOUND).format(manifestFile.absolutePath),
                I18n.translate(I18nKeys.Dialog.FILE_NOT_EXISTS),
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        try {
            Files.readString(manifestFile.toPath())
        } catch (e: Exception) {
            logger.warn("读取 AndroidManifest 失败", e)
        }

        gui.openFile(manifestFile)
    }

    private fun showMessage(message: String, title: String, messageType: Int) {
        javax.swing.SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(null, message, title, messageType)
        }
    }
}
