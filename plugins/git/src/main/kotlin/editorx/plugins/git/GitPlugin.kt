package editorx.plugins.git

import editorx.core.gui.CachedGuiViewProvider
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo

class GitPlugin : Plugin {
    override fun getInfo(): PluginInfo = PluginInfo(
        id = "git",
        name = "Git",
        version = "0.0.1",
    )

    override fun activate(pluginContext: PluginContext) {
        val guiContext = pluginContext.gui() ?: return
        
        guiContext.addActivityBarItem(
            "icons/git-branch.svg",
            object : CachedGuiViewProvider() {
                override fun createView(): javax.swing.JComponent {
                    return GitView(guiContext)
                }
            }
        )
    }
}

