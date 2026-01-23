package editorx.gui.workbench.agent

import editorx.gui.MainWindow
import editorx.gui.compose.ComposeHostPanel
import editorx.gui.theme.ThemeManager
import editorx.gui.widget.NoLineSplitPaneUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.SwingUtilities

/**
 * Agent 面板 - 使用 Compose UI 实现
 * 左右分割布局：
 * - 左侧：聊天页面（输入框、消息列表等）
 * - 右侧：会话记录页面（搜索框、New Agent按钮、Sessions列表）
 */
class AgentPanel(private val mainWindow: MainWindow) : JPanel(BorderLayout()) {

    private val leftComposePanel = ComposeHostPanel()
    private val rightComposePanel = ComposeHostPanel()
    private val splitPane: JSplitPane

    init {
        isOpaque = false
        layout = BorderLayout()
        
        // 设置左侧面板最小和最大宽度
        leftComposePanel.minimumSize = Dimension(300, 0)
        leftComposePanel.preferredSize = Dimension(500, 0)
        
        // 设置右侧面板最小和最大宽度
        rightComposePanel.minimumSize = Dimension(200, 0)
        rightComposePanel.preferredSize = Dimension(300, 0)
        rightComposePanel.maximumSize = Dimension(600, Int.MAX_VALUE)
        
        // 创建 JSplitPane 来处理拖动
        splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftComposePanel, rightComposePanel).apply {
            dividerLocation = 500  // 初始位置
            dividerSize = 1  // 分隔条宽度
            isOneTouchExpandable = false
            isContinuousLayout = true
            border = BorderFactory.createEmptyBorder()
            // 使用自定义 UI 来隐藏分隔条线条，但保留拖动功能
            ui = NoLineSplitPaneUI()
            // 设置 resizeWeight = 1.0，使容器缩放时优先调整左侧宽度，避免右侧被挤压到不可见
            resizeWeight = 1.0
        }
        
        // 监听分隔条位置变化，确保在合理范围内
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) {
            if (splitPane.width <= 0) {
                return@addPropertyChangeListener
            }
            val clampedLocation = clampDividerLocation(splitPane.dividerLocation)
            if (clampedLocation != splitPane.dividerLocation) {
                SwingUtilities.invokeLater {
                    splitPane.dividerLocation = clampedLocation
                }
            }
        }
        splitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                val clampedLocation = clampDividerLocation(splitPane.dividerLocation)
                if (clampedLocation != splitPane.dividerLocation) {
                    splitPane.dividerLocation = clampedLocation
                }
            }
        })
        
        add(splitPane, BorderLayout.CENTER)
        
        // 设置 Compose 内容
        leftComposePanel.setContent {
            LeftPanelCompose(mainWindow)
        }
        
        rightComposePanel.setContent {
            RightPanelCompose(mainWindow)
        }
        
        // 监听主题变更，更新分隔条颜色
        ThemeManager.addThemeChangeListener { updateTheme() }
        updateTheme()
        
        // 延迟设置初始位置，确保容器已经布局完成
        SwingUtilities.invokeLater {
            initializeDividerLocation()
        }
    }
    
    /**
     * 初始化分隔条位置，确保两个面板都可见
     */
    private fun initializeDividerLocation() {
        if (splitPane.width > 0) {
            // 初始位置：左侧 500px，右侧 300px
            val initialLeftWidth = 500
            splitPane.dividerLocation = clampDividerLocation(initialLeftWidth)
        }
    }
    
    private fun updateTheme() {
        val theme = ThemeManager.currentTheme
        background = theme.sidebarBackground
        splitPane.background = theme.sidebarBackground
        splitPane.dividerSize = 1
    }

    private fun clampDividerLocation(desired: Int): Int {
        if (splitPane.width <= 0) {
            return desired
        }
        val leftMin = leftComposePanel.minimumSize.width
        val rightMin = rightComposePanel.minimumSize.width
        val availableForLeft = (splitPane.width - rightMin - splitPane.dividerSize).coerceAtLeast(0)
        val minLocation = if (availableForLeft >= leftMin) leftMin else availableForLeft
        val maxLocation = minOf(800, availableForLeft).coerceAtLeast(minLocation)
        return desired.coerceIn(minLocation, maxLocation)
    }
}
