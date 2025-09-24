package editor.gui.ui.activitybar

import editor.gui.ViewArea
import editor.gui.ViewProvider
import editor.gui.ui.panel.Panel
import editor.gui.ui.sidebar.SideBar
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import javax.swing.*

class ActivityBar(_owner: JFrame) : JPanel() {
    var sideBar: SideBar? = null
    var panel: Panel? = null
    private val buttonGroup = ButtonGroup()
    private val buttonMap = mutableMapOf<String, JButton>()
    private val viewProviderMap = mutableMapOf<String, ViewProvider>()
    private val activeViews = mutableSetOf<String>()

    private val backgroundColor = Color.decode("#2c2c2c")
    private val selectedColor = Color.decode("#007acc")
    private val hoverColor = Color.decode("#404040")
    private val borderColor = Color.GRAY

    init {
        setupActivityBar()
    }

    private fun setupActivityBar() {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(50, 0)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, borderColor),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )
        background = backgroundColor
    }

    fun registerItem(id: String, icon: Icon, tooltip: String, viewProvider: ViewProvider) {
        val btn = createActivityButton(icon, tooltip, id)
        buttonGroup.add(btn)
        buttonMap[id] = btn
        viewProviderMap[id] = viewProvider
        add(btn)
        add(Box.createVerticalStrut(5))
        revalidate(); repaint()
    }

    private fun createActivityButton(icon: Icon, tooltip: String, viewId: String): JButton = JButton(icon).apply {
        toolTipText = tooltip
        margin = Insets(2, 2, 2, 2)
        preferredSize = Dimension(40, 40)
        minimumSize = Dimension(40, 40)
        maximumSize = Dimension(40, 40)
        isFocusPainted = false
        isBorderPainted = false
        isOpaque = true
        background = backgroundColor
        foreground = Color.WHITE
        alignmentX = java.awt.Component.CENTER_ALIGNMENT
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                if (background != selectedColor) background = hoverColor
            }

            override fun mouseExited(e: java.awt.event.MouseEvent) {
                if (background != selectedColor) background = backgroundColor
            }
        })
        addActionListener {
            handleButtonClick(viewId)
            updateButtonState(viewId)
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
                sideBar?.registerView(id, viewProvider.createView()); sideBar?.showView(id)
            }

            ViewArea.PANEL -> {
                panel?.registerView(id, null, viewProvider.createView()); panel?.showView(id)
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

