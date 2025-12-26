package editorx.gui.main.sidebar

import editorx.gui.ThemeManager
import editorx.gui.main.MainWindow
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.*

class SideBar(private val mainWindow: MainWindow) : JPanel() {
    companion object {
        const val MIN_WIDTH = 200
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
        isOpaque = true  // 确保背景色可见
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        isVisible = false
        // 初始化时隐藏SideBar
        updateVisibility()
        
        // 监听主题变更
        ThemeManager.addThemeChangeListener { updateTheme() }
    }
    
    private fun updateTheme() {
        background = ThemeManager.currentTheme.sidebarBackground
        revalidate()
        repaint()
        // 触发父容器 JSplitPane 的拖拽条重绘
        var current = parent
        while (current != null) {
            if (current is javax.swing.JSplitPane) {
                val splitPane = current
                // 通过 UI 访问 divider 并触发重绘
                (splitPane.ui as? javax.swing.plaf.basic.BasicSplitPaneUI)?.divider?.repaint()
                splitPane.repaint() // 也触发整个 splitPane 的重绘，确保 divider 更新
                break
            }
            current = current.parent
        }
    }

    fun showView(id: String, component: JComponent? = null, autoShow: Boolean = true) {
        // 如果视图不存在且提供了组件，则先注册
        if (!views.containsKey(id) && component != null) {
            views[id] = component
            add(component, id)
        }

        // 显示指定的视图
        if (views.containsKey(id)) {
            cardLayout.show(this, id)
            currentViewId = id
            // 显示SideBar（当实际不可见时强制展开到首选宽度）
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
            if (preserveNextDivider) {
                // 保留用户当前拖拽的位置，不主动设置分割条与尺寸，避免闪烁
                preserveNextDivider = false
            } else {
                minimumSize = Dimension(120, 0)
                preferredSize = Dimension(250, 0)
                updateDividerLocation(250) // 显示SideBar时，设置dividerLocation为300（和preferredSize保持一致）
            }
        } else {
            isVisible = false
            minimumSize = Dimension(0, 0)
            preferredSize = Dimension(0, 0)
            updateDividerLocation(0) // 隐藏SideBar时，设置dividerLocation为0（和preferredSize保持一致）
        }
        // 通知父容器重新布局
        parent?.revalidate()
    }

    private fun updateDividerLocation(location: Int) {
        // 查找包含SideBar的JSplitPane并更新其dividerLocation和dividerSize
        var current = parent
        while (current != null) {
            if (current is javax.swing.JSplitPane) {
                val split = current
                // 延迟到布局完成后再设置，避免初始化阶段被覆盖
                SwingUtilities.invokeLater {
                    split.dividerLocation = location
                    // 根据 SideBar 可见性调整拖拽条大小
                    split.dividerSize = if (location > 0) 4 else 0
                }
                break
            }
            current = current.parent
        }
    }

    /**
     * 下一次显示 SideBar 时保留分割条位置（用于用户手动拖拽打开），避免跳到 preferredSize 导致闪烁。
     */
    fun preserveNextDividerOnShow() {
        preserveNextDivider = true
    }

    fun hasView(id: String): Boolean = views.containsKey(id)
    fun getView(id: String): JComponent? = views[id]
    fun isSideBarVisible(): Boolean = isVisible

    /**
     * 同步内部状态与分割条位置
     * 当用户手动拖拽分割条时调用此方法来同步内部状态
     */
    fun syncVisibilityWithDivider() {
        var current = parent
        while (current != null) {
            if (current is javax.swing.JSplitPane) {
                val dividerLocation = current.dividerLocation
                val shouldBeVisible = dividerLocation > 0
                if (shouldBeVisible != isVisible) {
                    isVisible = shouldBeVisible
                }
                break
            }
            current = current.parent
        }
    }

    /**
     * 实际可见性：同时满足内部标记为可见，且分割条位置大于0
     * 用于避免由于异步设置 dividerLocation 导致的短暂不同步
     */
    fun isActuallyVisible(): Boolean {
        var current = parent
        while (current != null) {
            if (current is javax.swing.JSplitPane) {
                return isVisible && current.dividerLocation > 0
            }
            current = current.parent
        }
        return isVisible
    }
}
