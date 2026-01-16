package editorx.gui.workbench.sidebar

import editorx.gui.MainWindow
import editorx.gui.theme.ThemeManager
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.SwingUtilities

class RightSideBar(private val mainWindow: MainWindow) : JPanel() {
    companion object {
        const val MIN_WIDTH = 240
        const val DEFAULT_WIDTH = 320
    }

    private val cardLayout = CardLayout()
    private val views = mutableMapOf<String, JComponent>()
    private var currentViewId: String? = null
    private var isVisible = false
    private var preserveNextDivider: Boolean = false

    init {
        setupSideBar()
    }

    private fun setupSideBar() {
        layout = cardLayout
        updateTheme()
        isOpaque = true
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        isVisible = false
        updateVisibility()

        ThemeManager.addThemeChangeListener { updateTheme() }
    }

    private fun updateTheme() {
        background = ThemeManager.currentTheme.sidebarBackground
        revalidate()
        repaint()
        // 触发父容器 JSplitPane 的拖拽条重绘
        var current = parent
        while (current != null) {
            if (current is JSplitPane) {
                (current.ui as? javax.swing.plaf.basic.BasicSplitPaneUI)?.divider?.repaint()
                current.repaint()
                break
            }
            current = current.parent
        }
    }

    fun registerView(id: String, component: JComponent) {
        if (views.containsKey(id)) return
        views[id] = component
        add(component, id)
    }

    fun showView(id: String, component: JComponent? = null, autoShow: Boolean = true) {
        if (!views.containsKey(id) && component != null) {
            views[id] = component
            add(component, id)
        }

        if (views.containsKey(id)) {
            cardLayout.show(this, id)
            currentViewId = id
            if (autoShow && !isActuallyVisible()) {
                isVisible = true
                updateVisibility()
            }
            revalidate()
        }
    }

    fun getCurrentViewId(): String? = currentViewId

    fun getRegisteredViewIds(): Set<String> = views.keys.toSet()

    fun removeView(id: String) {
        views[id]?.let { component ->
            remove(component)
            views.remove(id)
            hideSideBar()
            revalidate()
        }
    }

    fun clearViews() {
        val keysToRemove = views.keys.filter { it != "default" }
        keysToRemove.forEach { removeView(it) }
        hideSideBar()
    }

    fun hideSideBar() {
        isVisible = false
        updateVisibility()
    }

    private fun updateVisibility() {
        if (isVisible) {
            isVisible = true
            if (preserveNextDivider) {
                preserveNextDivider = false
            } else {
                minimumSize = Dimension(MIN_WIDTH, 0)
                preferredSize = Dimension(DEFAULT_WIDTH, 0)
                updateDividerLocation(DEFAULT_WIDTH)
            }
        } else {
            isVisible = false
            minimumSize = Dimension(0, 0)
            preferredSize = Dimension(0, 0)
            updateDividerLocation(0)
        }
        parent?.revalidate()
    }

    private fun updateDividerLocation(rightWidth: Int) {
        var current = parent
        while (current != null) {
            if (current is JSplitPane) {
                val split = current
                SwingUtilities.invokeLater {
                    val availableWidth = if (split.width > 0) split.width else split.preferredSize.width
                    val targetLocation = if (rightWidth <= 0) {
                        split.maximumDividerLocation
                    } else {
                        (availableWidth - rightWidth)
                            .coerceAtLeast(split.minimumDividerLocation)
                            .coerceAtMost(split.maximumDividerLocation)
                    }
                    split.dividerLocation = targetLocation
                    split.dividerSize = if (rightWidth > 0) 4 else 0
                }
                break
            }
            current = current.parent
        }
    }

    fun preserveNextDividerOnShow() {
        preserveNextDivider = true
    }

    fun hasView(id: String): Boolean = views.containsKey(id)
    fun getView(id: String): JComponent? = views[id]
    fun isSideBarVisible(): Boolean = isVisible

    fun syncVisibilityFromDivider(visible: Boolean) {
        isVisible = visible
    }

    fun isActuallyVisible(): Boolean {
        var current = parent
        while (current != null) {
            if (current is JSplitPane) {
                return isVisible && current.dividerLocation < current.maximumDividerLocation
            }
            current = current.parent
        }
        return isVisible
    }
}
