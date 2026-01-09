package editorx.plugins.git

import editorx.core.gui.CachedGuiViewProvider
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo
import editorx.core.util.IconRef
import javax.swing.JComponent

class GitPlugin : Plugin {
    override fun getInfo() = PluginInfo(
        id = "git",
        name = "Git",
        version = "0.0.1",
    )

    override fun activate(pluginContext: PluginContext) {
        pluginContext.gui()?.addActivityBarItem(
            id = "git",
            iconRef = IconRef("icons/vcs.svg"),
            tooltip = "Source Control",
            viewProvider = object : CachedGuiViewProvider() {
                override fun createView(): JComponent {
                    return GitView(pluginContext.gui()!!)
                }
            },
        )
    }
}

