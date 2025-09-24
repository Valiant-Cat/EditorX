package editorx.gui.ui.panel

import editorx.gui.ui.MainWindow
import java.awt.CardLayout
import java.awt.Color
import javax.swing.*

class Panel(private val mainWindow: MainWindow) : JPanel() {
    private val cardLayout = CardLayout()
    private val views = mutableMapOf<String, JComponent>()
    private var currentViewId: String? = null
    private var isVisible = false

    init {
        setupPanel()
    }

    private fun setupPanel() {
        layout = cardLayout
        background = Color.WHITE
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        isVisible = false
        // 初始化时隐藏Panel
        updateVisibility()
    }

    fun showView(id: String, component: JComponent? = null) {
        // 如果视图不存在且提供了组件，则先注册
        if (!views.containsKey(id) && component != null) {
            views[id] = component
            add(component, id)
        }

        // 显示指定的视图
        if (views.containsKey(id)) {
            cardLayout.show(this, id)
            currentViewId = id
            // 显示Panel
            if (!isVisible) {
                isVisible = true
                updateVisibility()
            }
        }
    }

    fun getCurrentViewId(): String? = currentViewId
    fun getRegisteredViewIds(): Set<String> = views.keys.toSet()

    fun removeView(id: String) {
        views[id]?.let { component ->
            remove(component)
            views.remove(id)
            if (currentViewId == id) {
                // 检查是否还有其他视图
                if (views.isNotEmpty()) {
                    // 显示下一个可用的视图
                    val nextView = views.keys.first()
                    showView(nextView)
                } else {
                    // 没有其他视图，隐藏Panel
                    hidePanel()
                }
            }
            revalidate()
            repaint()
        }
    }

    fun clearViews() {
        val viewsToRemove = views.keys.toList()
        viewsToRemove.forEach { removeView(it) }
        hidePanel()
    }

    fun hidePanel() {
        isVisible = false
        updateVisibility()
    }

    private fun updateVisibility() {
        if (isVisible) {
            updateDividerLocation(700) // 显示Panel时设置合适的位置
        } else {
            updateDividerLocation(0) // 隐藏Panel时完全隐藏
        }
        // 通知父容器重新布局
        parent?.revalidate()
    }

    private fun updateDividerLocation(location: Int) {
        // 查找包含Panel的JSplitPane并更新其dividerLocation
        var current = parent
        while (current != null) {
            if (current is javax.swing.JSplitPane) {
                current.dividerLocation = location
                break
            }
            current = current.parent
        }
    }

    fun isPanelVisible(): Boolean = isVisible
}
