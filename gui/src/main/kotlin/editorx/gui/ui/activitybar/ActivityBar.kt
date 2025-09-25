package editorx.gui.ui.activitybar

import editorx.gui.Constants
import editorx.gui.theme.ThemeManager
import editorx.gui.SideBarViewProvider
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
    private val viewProviderMap = mutableMapOf<String, SideBarViewProvider>()
    private var activeId: String? = null
    private var autoSelected: Boolean = false

    private val backgroundColor = ThemeManager.activityBarBackground
    private val selectedColor = ThemeManager.activityBarItemSelected
    private val hoverColor = ThemeManager.activityBarItemHover

    init {
        setupActivityBar()
    }

    private fun setupActivityBar() {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(44, 0)
        minimumSize = Dimension(44, 0)
        maximumSize = Dimension(44, Int.MAX_VALUE)
        // 在靠近可拖拽区域一侧增加一条细分割线以增强层次
        val separator = ThemeManager.separator
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, separator),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )
        background = backgroundColor
    }

    fun addItem(id: String, tooltip: String, icon: Icon, viewProvider: SideBarViewProvider) {
        val btn = createActivityButton(icon, tooltip, id)
        val wasEmpty = buttonMap.isEmpty()
        buttonGroup.add(btn)
        buttonMap[id] = btn
        viewProviderMap[id] = viewProvider
        add(btn)
        add(Box.createVerticalStrut(5))
        revalidate(); repaint()

        // 默认选中逻辑（无持久化）：
        // 1) 若注册的是配置中的默认插件，则默认选中它
        // 2) 否则在第一个条目注册完成时，默认激活第一个
        // 3) 若之前是自动选中的非默认，后续默认插件注册时，切换到默认插件
        val isPreferred = id == Constants.ACTIVITY_BAR_DEFAULT_ID
        when {
            // 尚未有任何选中：优先选择首选，否则选择第一个
            activeId == null && isPreferred -> {
                handleButtonClick(id, userInitiated = false)
                autoSelected = true
                updateAllButtonStates()
            }

            activeId == null && wasEmpty -> {
                handleButtonClick(id, userInitiated = false)
                autoSelected = true
                updateAllButtonStates()
            }
            // 若当前是自动选中的非首选，而新来的正好是首选，则切换到首选
            isPreferred && autoSelected && activeId != id -> {
                handleButtonClick(id, userInitiated = false)
                autoSelected = true
                updateAllButtonStates()
            }
        }
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
                    if (activeId != viewId) background = hoverColor
                    repaint()
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    if (activeId != viewId) background = backgroundColor
                    repaint()
                }
            })
            addActionListener {
                handleButtonClick(viewId, userInitiated = true)
                updateAllButtonStates()
            }
        }
    }

    private fun handleButtonClick(id: String, userInitiated: Boolean = false) {
        val viewProvider = viewProviderMap[id] ?: return
        // VSCode 模式：ActivityBar 仅控制 SideBar
        val isCurrentlyDisplayed = sideBar?.getCurrentViewId() == id && sideBar?.isSideBarVisible() == true
        if (isCurrentlyDisplayed) {
            sideBar?.hideSideBar(); activeId = null
            // 用户触发隐藏时，视为用户决定
            if (userInitiated) autoSelected = false
        } else {
            sideBar?.showView(id, viewProvider.getView()); activeId = id
            // 被用户触发的选中将覆盖自动选中状态
            autoSelected = !userInitiated
        }
    }

    // 不再支持 ActivityBar 直接展示到底部 Panel
    private fun showView(id: String, viewProvider: SideBarViewProvider) {
        sideBar?.showView(id, viewProvider.getView())
    }

    private fun hideView(id: String) {
        if (sideBar?.getCurrentViewId() == id) sideBar?.hideSideBar()
    }

    private fun updateAllButtonStates() {
        buttonMap.forEach { (id, btn) ->
            btn.background = if (activeId == id) selectedColor else backgroundColor
            btn.repaint()
        }
    }

    fun removeviewProvider(id: String) {
        buttonMap[id]?.let { button ->
            buttonGroup.remove(button)
            remove(button)
            buttonMap.remove(id)
            viewProviderMap.remove(id)
            if (activeId == id) activeId = null
            revalidate(); repaint()
        }
    }

    fun clearviewProviders() {
        buttonMap.values.forEach { button -> buttonGroup.remove(button); remove(button) }
        buttonMap.clear(); viewProviderMap.clear(); activeId = null; revalidate(); repaint()
    }
}
