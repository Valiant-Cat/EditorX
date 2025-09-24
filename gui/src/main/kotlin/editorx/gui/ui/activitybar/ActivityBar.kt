package editorx.gui.ui.activitybar

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

    private val backgroundColor = Color.decode("#f4f4f4")
    private val selectedColor = Color.decode("#007acc")
    private val hoverColor = Color.decode("#404040")
    private val borderColor = Color.GRAY

    init {
        setupActivityBar()
    }

    private fun setupActivityBar() {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(40, 0)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, borderColor),
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
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                
                // 绘制圆角背景
                val shape = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), 8f, 8f)
                g2d.color = background
                g2d.fill(shape)
                
                // 绘制图标
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
        if (activeViews.contains(id)) {
            hideView(id, viewProvider.area())
            activeViews.remove(id)
        } else {
            showView(id, viewProvider)
            activeViews.add(id)
        }
    }

    private fun showView(id: String, viewProvider: ViewProvider) {
        when (viewProvider.area()) {
            ViewArea.SIDEBAR -> {
                sideBar?.registerView(id, viewProvider.getView()); sideBar?.showView(id)
            }

            ViewArea.PANEL -> {
                panel?.registerView(id, null, viewProvider.getView()); panel?.showView(id)
            }
        }
    }

    private fun hideView(id: String, displayLocation: ViewArea) {
        when (displayLocation) {
            ViewArea.SIDEBAR -> sideBar?.removeView(id)
            ViewArea.PANEL -> panel?.removeView(id)
        }
    }

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
