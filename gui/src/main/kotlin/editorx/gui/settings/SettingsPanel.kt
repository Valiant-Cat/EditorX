package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

/**
 * 设置子页面的抽象基类。
 * 提供临时更改缓存机制和"还原更改"按钮。
 */
abstract class SettingsPanel : JPanel(null) { // 使用 null layout 以便绝对定位还原按钮
    
    /**
     * 临时更改缓存，用于存储未保存的更改
     */
    private val pendingChanges = mutableMapOf<String, Any?>()
    
    /**
     * 还原更改按钮（位于右上角）
     */
    private val revertButton = JButton(I18n.translate(I18nKeys.Action.REVERT_CHANGES)).apply {
        isVisible = false
        minimumSize = Dimension(100, 28)
        addActionListener { 
            revertChanges()
            updateRevertButtonVisibility()
        }
    }
    
    /**
     * 内容面板（子类应该将主要内容添加到这里）
     */
    protected val contentPanel = JPanel(BorderLayout())
    
    init {
        // 使用自定义布局管理器，支持绝对定位还原按钮
        layout = object : LayoutManager {
            override fun addLayoutComponent(name: String?, comp: Component?) {}
            override fun removeLayoutComponent(comp: Component?) {}
            override fun preferredLayoutSize(parent: Container?): Dimension {
                return contentPanel.preferredSize
            }
            override fun minimumLayoutSize(parent: Container?): Dimension {
                return contentPanel.minimumSize
            }
            override fun layoutContainer(parent: Container?) {
                val width = parent?.width ?: 0
                val height = parent?.height ?: 0
                
                // 内容面板占据整个空间
                contentPanel.setBounds(0, 0, width, height)
                
                // 还原按钮位于右上角，固定位置，不影响布局
                if (revertButton.isVisible) {
                    val buttonWidth = revertButton.preferredSize.width
                    val buttonHeight = revertButton.preferredSize.height
                    val margin = 8
                    revertButton.setBounds(
                        width - buttonWidth - margin,
                        margin,
                        buttonWidth,
                        buttonHeight
                    )
                }
            }
        }
        
        add(revertButton)
        add(contentPanel)
    }
    
    /**
     * 检查是否有待保存的更改（需要子类实现具体逻辑）
     */
    protected abstract fun hasActualPendingChanges(): Boolean
    
    /**
     * 检查是否有待保存的更改
     */
    fun hasPendingChanges(): Boolean = hasActualPendingChanges()
    
    /**
     * 更新还原按钮的可见性
     */
    protected fun updateRevertButtonVisibility() {
        val hasChanges = hasActualPendingChanges()
        revertButton.isVisible = hasChanges
        if (hasChanges) {
            // 确保按钮在最上层
            setComponentZOrder(revertButton, 0)
        }
        revalidate()
        repaint()
    }
    
    /**
     * 记录一个临时更改
     */
    protected fun <T> setPendingChange(key: String, value: T) {
        pendingChanges[key] = value
        updateRevertButtonVisibility()
        onPendingChange()
        notifyDialogOfChanges()
    }
    
    /**
     * 通知对话框有更改（用于更新导航项颜色）
     */
    private fun notifyDialogOfChanges() {
        // 通过父容器找到 SettingsDialog 并通知它
        var parent = parent
        while (parent != null) {
            if (parent is SettingsDialog) {
                parent.onPanelChangesUpdated()
                break
            }
            parent = parent.parent
        }
    }
    
    /**
     * 获取一个临时更改的值
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> getPendingChange(key: String): T? {
        return pendingChanges[key] as? T
    }
    
    /**
     * 清除所有临时更改
     */
    protected fun clearPendingChanges() {
        pendingChanges.clear()
        updateRevertButtonVisibility()
        notifyDialogOfChanges()
    }
    
    /**
     * 清除指定的临时更改
     */
    protected fun clearPendingChange(key: String) {
        pendingChanges.remove(key)
        updateRevertButtonVisibility()
        notifyDialogOfChanges()
    }
    
    /**
     * 还原所有更改（由还原按钮调用）
     */
    protected open fun revertChanges() {
        pendingChanges.clear()
        refresh()
        updateRevertButtonVisibility()
        notifyDialogOfChanges()
    }
    
    /**
     * 当有临时更改时的回调（子类可以重写以更新UI）
     */
    protected open fun onPendingChange() {
        // 子类可以重写此方法以响应更改
    }
    
    /**
     * 应用所有更改（点击确定时调用）
     * @return 如果需要重启，返回 true
     */
    abstract fun applyChanges(): Boolean
    
    /**
     * 刷新面板内容（切换页面或还原更改时调用）
     */
    abstract fun refresh()
}

