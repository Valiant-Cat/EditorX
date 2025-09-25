package editorx.plugins.testplugin

import editorx.gui.CachedViewProvider
import editorx.gui.ViewArea
import editorx.plugin.Plugin
import editorx.plugin.PluginContext
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class TestPlugin : Plugin {
    override fun activate(context: PluginContext) {
        // Register a simple panel view
        context.addActivityBarItem(
            iconPath = "icons/explorer.svg", // reuse existing icon from explorer for demo
            viewProvider = object : CachedViewProvider() {
                override fun createView(): javax.swing.JComponent {
                    val panel = JPanel(BorderLayout())
                    val label = JLabel("Hello from TestPlugin!")
                    val btn = JButton("Run Command: app.about").apply {
                        addActionListener { context.commands().execute("app.about") }
                    }
                    panel.add(label, BorderLayout.CENTER)
                    panel.add(btn, BorderLayout.SOUTH)
                    return panel
                }

                override fun area(): ViewArea = ViewArea.SIDEBAR
            }
        )

        // Register a command contributed by this plugin
        if (!context.commands().has("test.hello")) {
            context.commands().register(
                editorx.command.CommandMeta("test.hello", "测试: 打印日志", "来自TestPlugin的命令")
            ) {
                val logger = java.util.logging.Logger.getLogger("TestPlugin")
                logger.info("Hello from TestPlugin command!")
            }
        }
    }
}
