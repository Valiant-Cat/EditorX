package editorx.plugins.stringfog

import editorx.core.gui.EditorContextMenuItem
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class StringFogDecryptPlugin : Plugin {
    companion object {
        private val logger = LoggerFactory.getLogger(StringFogDecryptPlugin::class.java)
    }

    override fun getInfo() = PluginInfo(
        id = "stringfog",
        name = "StringFog 解密",
        version = "0.0.1",
    )

    private var pluginContext: PluginContext? = null

    override fun activate(pluginContext: PluginContext) {
        this.pluginContext = pluginContext
        val gui = pluginContext.gui() ?: return

        gui.registerEditorContextMenuItem(
            EditorContextMenuItem(
                id = "stringfog.decrypt",
                text = "StringFog 解密",
                visibleWhen = { view ->
                    view.languageId == "smali" || view.file?.name?.endsWith(".smali", ignoreCase = true) == true
                },
                enabledWhen = { view ->
                    view.editable
                },
                action = action@{ handler ->
                    val view = handler.view
                    if (!view.editable) {
                        showMessage("当前文件为只读，无法解密", "StringFog 解密", JOptionPane.WARNING_MESSAGE)
                        return@action
                    }

                    val original = handler.getText()
                    val formatter = StringFogSmaliDecryptFormatter()
                    val result = formatter.decryptWithReport(original)
                    if (result.replacedCalls <= 0) {
                        if (result.foundCalls <= 0) {
                            showMessage("未发现 StringFog.decrypt 调用", "StringFog 解密", JOptionPane.INFORMATION_MESSAGE)
                        } else {
                            showMessage(
                                "发现 ${result.foundCalls} 处 StringFog.decrypt，但未能解密（可能不是 bytes + fill-array-data 形式）",
                                "StringFog 解密",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        }
                        return@action
                    }
                    logger.info("StringFog 解密已应用: {}", view.file?.name ?: "<unknown>")
                    handler.replaceText(result.content)
                }
            )
        )
    }

    override fun deactivate() {
        pluginContext?.gui()?.unregisterAllEditorContextMenuItems()
        pluginContext = null
    }

    private fun showMessage(message: String, title: String, messageType: Int) {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(null, message, title, messageType)
        }
    }
}
