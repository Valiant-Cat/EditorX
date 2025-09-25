package editorx.gui.ui.panel

import editorx.gui.ui.MainWindow
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.*

class Panel(private val mainWindow: MainWindow) : JPanel() {
    private val tabbedPane = JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT)
    private val views = mutableMapOf<String, JComponent>()
    private var isVisible = false

    init { setupPanel() }

    private fun setupPanel() {
        layout = BorderLayout()
        background = Color.WHITE
        // 保持干净，不再额外加一条分割线
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        add(tabbedPane, BorderLayout.CENTER)
        updateVisibility()
    }

    fun showView(id: String, component: JComponent? = null) {
        if (!views.containsKey(id) && component != null) {
            views[id] = component
            tabbedPane.addTab(id, component)
        }
        val idx = tabbedPane.indexOfTab(id)
        if (idx >= 0) tabbedPane.selectedIndex = idx
        if (!isVisible) { isVisible = true; updateVisibility() }
    }

    fun getCurrentViewId(): String? = if (tabbedPane.selectedIndex >= 0) tabbedPane.getTitleAt(tabbedPane.selectedIndex) else null
    fun getRegisteredViewIds(): Set<String> = views.keys.toSet()

    fun removeView(id: String) {
        val idx = tabbedPane.indexOfTab(id)
        if (idx >= 0) {
            val comp = tabbedPane.getComponentAt(idx) as? JComponent
            tabbedPane.removeTabAt(idx)
            comp?.let { views.remove(id) }
            if (tabbedPane.tabCount == 0) hidePanel()
            revalidate(); repaint()
        }
    }

    fun clearViews() {
        tabbedPane.removeAll(); views.clear(); hidePanel()
    }

    fun hidePanel() { isVisible = false; updateVisibility() }

    private fun updateVisibility() {
        val split = findParentSplit()
        if (isVisible) {
            split?.dividerSize = 8
            updateDividerLocation(700)
        } else {
            // 保留一个可拖拽的握柄（8px），并把分割条移动到可用的最大位置
            split?.dividerSize = 8
            split?.let { sp ->
                val target = sp.maximumDividerLocation
                sp.dividerLocation = target
            }
        }
        parent?.revalidate()
    }

    private fun updateDividerLocation(location: Int) {
        val split = findParentSplit()
        split?.dividerLocation = location
    }

    private fun findParentSplit(): javax.swing.JSplitPane? {
        var current = parent
        while (current != null) {
            if (current is javax.swing.JSplitPane) return current
            current = current.parent
        }
        return null
    }

    fun isPanelVisible(): Boolean = isVisible
}
