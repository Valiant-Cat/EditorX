package editorx.gui.ui.activitybar

import editorx.gui.theme.ThemeManager
import editorx.gui.ViewArea
import editorx.gui.ViewProvider
import editorx.gui.ui.MainWindow
import editorx.gui.ui.panel.Panel
import editorx.gui.ui.sidebar.SideBar
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.*

class ActivityBar(private val mainWindow: MainWindow) : JPanel() {
    var sideBar: SideBar? = null
    var panel: Panel? = null
    private val buttonGroup = ButtonGroup()
    private val buttonMap = mutableMapOf<String, JButton>()
    private val viewProviderMap = mutableMapOf<String, ViewProvider>()
    private val activeViews = mutableSetOf<String>()

    private val backgroundColor = Color.decode("#f2f2f2")
    private val selectedColor = ThemeManager.palette.primaryContainer
    private val hoverColor = ThemeManager.palette.surfaceVariant

    init {
        setupActivityBar()
    }

    private fun setupActivityBar() {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(44, 0)
        minimumSize = Dimension(44, 0)
        maximumSize = Dimension(44, Int.MAX_VALUE)
        // 在靠近可拖拽区域一侧增加一条细分割线以增强层次
        val separator = Color(0xDE, 0xDE, 0xDE)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, separator),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )
        background = backgroundColor
    }

    fun addItem(id: String, tooltip: String, icon: Icon, viewProvider: ViewProvider) {
        val btn = createActivityButton(icon, tooltip, id)
        buttonGroup.add(btn)
        buttonMap[id] = btn
        viewProviderMap[id] = viewProvider
        add(btn)
        add(Box.createVerticalStrut(5))
        revalidate(); repaint()
    }

    private fun createActivityButton(icon: Icon, tooltip: String, viewId: String): JButton {
        return object : JButton(icon) {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val shape = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), 8f, 8f)
                    g2.color = background
                    g2.fill(shape)
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }.apply {
            toolTipText = tooltip
            margin = Insets(2, 2, 2, 2)
            preferredSize = Dimension(32, 32)
            minimumSize = Dimension(32, 32)
            maximumSize = Dimension(32, 32)
            isFocusPainted = false
            isBorderPainted = false
            isOpaque = false  // 设置为false，因为我们自定义绘制背景
            background = backgroundColor
            foreground = Color.WHITE
            alignmentX = Component.CENTER_ALIGNMENT
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    if (background != selectedColor) background = hoverColor
                    repaint()
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    if (background != selectedColor) background = backgroundColor
                    repaint()
                }
            })
            addActionListener {
                handleButtonClick(viewId)
                updateButtonState(viewId)
            }
        }
    }

    private fun handleButtonClick(id: String) {
        val viewProvider = viewProviderMap[id] ?: return
        // VSCode 模式：ActivityBar 仅控制 SideBar
        val isCurrentlyDisplayed = sideBar?.getCurrentViewId() == id && sideBar?.isSideBarVisible() == true
        if (isCurrentlyDisplayed) {
            sideBar?.hideSideBar(); activeViews.remove(id)
        } else {
            sideBar?.showView(id, viewProvider.getView()); activeViews.add(id)
        }
    }

    // 不再支持 ActivityBar 直接展示到底部 Panel
    private fun showView(id: String, viewProvider: ViewProvider) { sideBar?.showView(id, viewProvider.getView()) }
    private fun hideView(id: String) { if (sideBar?.getCurrentViewId() == id) sideBar?.hideSideBar() }

    private fun updateButtonState(id: String) {
        val button = buttonMap[id] ?: return
        button.background = if (activeViews.contains(id)) selectedColor else backgroundColor
        button.repaint()
    }

    fun removeviewProvider(id: String) {
        buttonMap[id]?.let { button ->
            buttonGroup.remove(button)
            remove(button)
            buttonMap.remove(id)
            viewProviderMap.remove(id)
            activeViews.remove(id)
            revalidate(); repaint()
        }
    }

    fun clearviewProviders() {
        buttonMap.values.forEach { button -> buttonGroup.remove(button); remove(button) }
        buttonMap.clear(); viewProviderMap.clear(); activeViews.clear(); revalidate(); repaint()
    }
}
