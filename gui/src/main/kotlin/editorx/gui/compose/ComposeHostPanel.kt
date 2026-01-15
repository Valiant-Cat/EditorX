package editorx.gui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * 在 Swing 容器中承载 Compose UI 的桥接面板。
 *
 * 目标：让现有 Swing UI 可以渐进式引入 Compose（例如逐步替换部分对话框/面板）。
 */
class ComposeHostPanel : JPanel(BorderLayout()) {
    private val composePanel = ComposePanel()

    init {
        isOpaque = false
        add(composePanel, BorderLayout.CENTER)
    }

    fun setContent(content: @Composable () -> Unit) {
        composePanel.setContent(content)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun removeNotify() {
        runCatching { composePanel.dispose() }
        super.removeNotify()
    }
}
