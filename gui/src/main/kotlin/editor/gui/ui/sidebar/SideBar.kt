package editor.gui.ui.sidebar

import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.*

class SideBar(_owner: JFrame) : JPanel() {
    private val cardLayout = CardLayout()
    private val views = mutableMapOf<String, JComponent>()
    private var currentViewId: String? = null
    private var defaultViewId: String? = null

    init { setupSideBar() }

    private fun setupSideBar() {
        layout = cardLayout
        preferredSize = Dimension(300, 0)
        minimumSize = Dimension(200, 0)
        background = Color.WHITE
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        createDefaultView()
    }

    private fun createDefaultView() {
        val defaultView = JPanel().apply {
            layout = java.awt.BorderLayout()
            background = Color.WHITE
            val label = JLabel("欢迎使用 APK Editor").apply {
                horizontalAlignment = JLabel.CENTER
                verticalAlignment = JLabel.CENTER
                font = font.deriveFont(java.awt.Font.BOLD, 16f)
                foreground = Color.GRAY
            }
            add(label, java.awt.BorderLayout.CENTER)
        }
        registerView("default", defaultView)
        defaultViewId = "default"
        showView("default")
    }

    fun registerView(id: String, component: JComponent) {
        views[id] = component
        add(component, id)
        if (defaultViewId == null && views.size == 1) defaultViewId = id
    }

    fun showView(id: String) {
        if (views.containsKey(id)) {
            cardLayout.show(this, id)
            currentViewId = id
            revalidate(); repaint()
        } else {
            defaultViewId?.let { showView(it) }
        }
    }

    fun getCurrentViewId(): String? = currentViewId
    fun getRegisteredViewIds(): Set<String> = views.keys.toSet()

    fun removeView(id: String) {
        views[id]?.let { component ->
            remove(component)
            views.remove(id)
            if (currentViewId == id) defaultViewId?.let { showView(it) }
            revalidate(); repaint()
        }
    }

    fun clearViews() {
        val keysToRemove = views.keys.filter { it != "default" }
        keysToRemove.forEach { removeView(it) }
    }

    fun hasView(id: String): Boolean = views.containsKey(id)
    fun getView(id: String): JComponent? = views[id]
}

