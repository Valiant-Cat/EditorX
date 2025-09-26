package editorx.gui.workbench.sidebar

import editorx.gui.MainWindow
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.*

class SideBar(private val mainWindow: MainWindow) : JPanel() {
    private val cardLayout = CardLayout()
    private val views = mutableMapOf<String, JComponent>()
    private var currentViewId: String? = null
    private var isVisible = false

    init {
        setupSideBar()
    }

    private fun setupSideBar() {
        layout = cardLayout
        background = Color.WHITE
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        isVisible = false
        // 初始化时隐藏SideBar
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
            // 显示SideBar
            if (!isVisible) {
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
            if (currentViewId == id) {
                // 检查是否还有其他非默认视图
                val hasNonDefaultViews = views.keys.any { it != "default" }
                if (hasNonDefaultViews) {
                    // 显示下一个可用的视图
                    val nextView = views.keys.firstOrNull { it != "default" }
                    nextView?.let { showView(it) }
                } else {
                    // 没有其他视图，隐藏SideBar
                    hideSideBar()
                }
            }
            revalidate()
        }
    }

    fun clearViews() {
        val keysToRemove = views.keys.filter { it != "default" }
        keysToRemove.forEach { removeView(it) }
        // 清除所有视图后隐藏SideBar
        hideSideBar()
    }

    fun hideSideBar() {
        isVisible = false
        updateVisibility()
    }

    private fun updateVisibility() {
        // 通过设置visible属性和调整JSplitPane的dividerLocation来控制SideBar的显示/隐藏
        if (isVisible) {
            isVisible = true
            preferredSize = Dimension(300, 0)
            minimumSize = Dimension(200, 0)
            // 显示SideBar时，设置dividerLocation为300
            updateDividerLocation(300)
        } else {
            isVisible = false
            preferredSize = Dimension(0, 0)
            minimumSize = Dimension(0, 0)
            // 隐藏SideBar时，设置dividerLocation为0
            updateDividerLocation(0)
        }
        // 通知父容器重新布局
        parent?.revalidate()
    }

    private fun updateDividerLocation(location: Int) {
        // 查找包含SideBar的JSplitPane并更新其dividerLocation
        var current = parent
        while (current != null) {
            if (current is javax.swing.JSplitPane) {
                current.dividerLocation = location
                break
            }
            current = current.parent
        }
    }

    fun hasView(id: String): Boolean = views.containsKey(id)
    fun getView(id: String): JComponent? = views[id]
    fun isSideBarVisible(): Boolean = isVisible
}
