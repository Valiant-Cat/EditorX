package editorx.plugins.git

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
        // 注意：addActivityBarItem 已不再支持
        // 插件不再支持在 SideBar 中注册视图
    }
}

