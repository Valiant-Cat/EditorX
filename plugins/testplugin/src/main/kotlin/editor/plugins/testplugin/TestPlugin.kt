package editor.plugins.testplugin

import editor.gui.CachedViewProvider
import editor.gui.ViewArea
import editor.gui.ViewProvider
import editor.plugin.Plugin
import editor.plugin.PluginContext
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.*

/**
 * 测试插件 - 用于验证源码扫描功能
 */
class TestPlugin : Plugin {

    private var context: PluginContext? = null

    override fun activate(context: PluginContext) {
        this.context = context

        context.addActivityBarItem(
            "test",
            "", // 使用默认图标
            "测试插件 (源码扫描)",
            object : CachedViewProvider() {
                override fun createView(): JComponent {
                    return createTestView()
                }

                override fun area(): ViewArea = ViewArea.PANEL
            }
        )
        println("TestPlugin插件已启动 (源码扫描)")
    }

    private fun createTestView(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 标题
        val titleLabel = JLabel("测试插件 - 源码扫描").apply {
            font = font.deriveFont(16f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = Color.BLUE
        }
        panel.add(titleLabel, BorderLayout.NORTH)
        
        // 内容区域
        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
        }
        
        // 插件信息
        val pluginInfo = context?.getLoadedPlugin()
        val infoText = """
            插件名称: ${pluginInfo?.name ?: "未知"}
            插件版本: ${pluginInfo?.version ?: "未知"}
            插件描述: ${pluginInfo?.description ?: "未知"}
            加载方式: 源码扫描
            类路径: ${this::class.java.name}
        """.trimIndent()
        
        val infoArea = JTextArea(infoText).apply {
            isEditable = false
            background = Color(240, 240, 240)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            )
        }
        contentPanel.add(infoArea)
        
        contentPanel.add(Box.createVerticalStrut(20))
        
        // 测试按钮
        val testButton = JButton("测试功能").apply {
            addActionListener { 
                JOptionPane.showMessageDialog(
                    panel, 
                    "测试插件功能正常！\n这是通过源码扫描加载的插件。", 
                    "测试结果", 
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        }
        contentPanel.add(testButton)
        
        panel.add(contentPanel, BorderLayout.CENTER)
        
        return panel
    }
}
