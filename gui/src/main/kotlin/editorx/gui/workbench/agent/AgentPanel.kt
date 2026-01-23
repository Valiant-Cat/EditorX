package editorx.gui.workbench.agent

import editorx.gui.MainWindow
import editorx.gui.compose.ComposeHostPanel
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Agent 面板 - 使用 Compose UI 实现
 * 从上到下布局：
 * - 顶部：工具栏
 * - 中间：聊天记录区域（可滚动）
 * - 底部：输入框区域
 */
class AgentPanel(private val mainWindow: MainWindow) : JPanel(BorderLayout()) {

    private val composePanel = ComposeHostPanel()

    init {
        isOpaque = false
        layout = BorderLayout()
        
        add(composePanel, BorderLayout.CENTER)
        
        // 设置 Compose 内容
        composePanel.setContent {
            AgentPanelCompose(mainWindow)
        }
    }
}
