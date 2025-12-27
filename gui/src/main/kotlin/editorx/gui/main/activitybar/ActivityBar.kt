package editorx.gui.main.activitybar

import editorx.core.util.IconRef
import editorx.core.gui.GuiViewProvider
import editorx.gui.Constants
import editorx.gui.theme.ThemeManager
import editorx.gui.main.MainWindow
import editorx.core.util.IconLoader
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.*

class ActivityBar(private val mainWindow: MainWindow) : JPanel() {
    companion object {
        private const val ICON_SIZE = 20
    }

    private val buttonGroup = ButtonGroup()
    private val buttonMap = mutableMapOf<String, JButton>()
    private val guiViewProviderMap = mutableMapOf<String, GuiViewProvider>()
    private var activeId: String? = null
    private var autoSelected: Boolean = false

    private val backgroundColor = ThemeManager.activityBarBackground
    private val itemNormalBackgroundColor = backgroundColor
    private val itemSelectedBackgroundColor = ThemeManager.activityBarItemSelectedBackground
    private val hoverColor = ThemeManager.activityBarItemHoverBackground

    init {
        setupActivityBar()
    }

    private fun setupActivityBar() {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(38, 0)
        minimumSize = Dimension(38, 0)
        maximumSize = Dimension(38, Int.MAX_VALUE)
        // 在靠近可拖拽区域一侧增加一条细分割线以增强层次
        val separator = ThemeManager.separator
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, separator),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )
        background = backgroundColor
    }

    fun addItem(id: String, tooltip: String, iconPath: String, viewProvider: GuiViewProvider) {
        // 若重复注册同一 id（例如插件启停/重载），先清理旧项，避免 ButtonGroup 持有多余引用
        removeViewProvider(id)

        val icon = IconLoader.getIcon(IconRef(iconPath), ICON_SIZE) ?: createDefaultIcon()
        val btn = createActivityButton(icon, tooltip, id)
        val wasEmpty = buttonMap.isEmpty()
        buttonGroup.add(btn)
        buttonMap[id] = btn
        guiViewProviderMap[id] = viewProvider

        // 按照排序顺序重新排列所有按钮
        reorderButtons()

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

    private fun createDefaultIcon(): Icon {
        return object : Icon {
            override fun getIconWidth(): Int = ICON_SIZE
            override fun getIconHeight(): Int = ICON_SIZE

            override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
                val g2 = g.create() as Graphics2D
                try {
                    val oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                    val oldPaint = g2.paint
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = Color(108, 112, 126)
                    g2.fillRect(x + 2, y + 2, ICON_SIZE - 4, ICON_SIZE - 4)
                    g2.paint = oldPaint
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA)
                } finally {
                    g2.dispose()
                }
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
            preferredSize = Dimension(28, 28)
            minimumSize = Dimension(28, 28)
            maximumSize = Dimension(28, 28)
            isFocusPainted = false
            isBorderPainted = false
            isOpaque = false  // 设置为false，因为我们自定义绘制背景
            background = itemNormalBackgroundColor
            foreground = Color.WHITE
            alignmentX = Component.CENTER_ALIGNMENT
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    if (activeId != viewId) background = hoverColor
                    repaint()
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    if (activeId != viewId) background = itemNormalBackgroundColor
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
        val viewProvider = guiViewProviderMap[id] ?: return
        // VSCode 模式：ActivityBar 仅控制 SideBar
        val isCurrentlyDisplayed =
            mainWindow.sideBar.getCurrentViewId() == id && mainWindow.sideBar.isActuallyVisible() == true
        if (isCurrentlyDisplayed) {
            mainWindow.sideBar.hideSideBar(); activeId = null
            // 用户触发隐藏时，视为用户决定
            if (userInitiated) autoSelected = false
        } else {
            mainWindow.sideBar.showView(id, viewProvider.getView()); activeId = id
            // 被用户触发的选中将覆盖自动选中状态
            autoSelected = !userInitiated
        }
    }

    /**
     * 供外部在分割条被手动拖动等场景下同步按钮状态。
     */
    fun activateItem(id: String, userInitiated: Boolean = false) {
        if (!guiViewProviderMap.containsKey(id)) return
        if (activeId == id && mainWindow.sideBar.isActuallyVisible()) {
            updateAllButtonStates(); return
        }
        handleButtonClick(id, userInitiated)
        updateAllButtonStates()
    }

    /**
     * 清除当前激活态（例如用户手动拖拽分割条隐藏 SideBar 时）。
     */
    fun clearActive() {
        activeId = null
        autoSelected = false
        updateAllButtonStates()
    }

    /**
     * 仅同步高亮状态，不触发展示/隐藏逻辑。
     * 用于用户拖拽分割条打开 SideBar 时与当前视图保持一致。
     */
    fun highlightOnly(id: String) {
        if (!buttonMap.containsKey(id)) return
        activeId = id
        autoSelected = false
        updateAllButtonStates()
    }

    private fun updateAllButtonStates() {
        buttonMap.forEach { (id, btn) ->
            btn.background = if (activeId == id) itemSelectedBackgroundColor else itemNormalBackgroundColor
            btn.repaint()
        }
    }

    fun removeviewProvider(id: String) {
        // 兼容旧命名（内部委托到 removeViewProvider）
        removeViewProvider(id)
    }

    fun removeViewProvider(id: String) {
        val button = buttonMap.remove(id) ?: return
        buttonGroup.remove(button)
        guiViewProviderMap.remove(id)
        if (activeId == id) activeId = null
        reorderButtons()
        updateAllButtonStates()
        revalidate()
        repaint()
    }

    fun clearviewProviders() {
        // 兼容旧命名
        clearViewProviders()
    }

    fun clearViewProviders() {
        buttonMap.values.forEach { button -> buttonGroup.remove(button) }
        buttonMap.clear()
        guiViewProviderMap.clear()
        activeId = null
        removeAll()
        revalidate()
        repaint()
    }

    /**
     * 按照Constants中定义的顺序重新排列按钮
     */
    private fun reorderButtons() {
        // 移除所有现有组件
        removeAll()

        // 按照排序顺序添加按钮
        val sortedIds = buttonMap.keys.sortedBy { id ->
            return@sortedBy Constants.ACTIVITY_BAR_ORDER.indexOf(id).let { index ->
                if (index >= 0) index else Int.MAX_VALUE
            }
        }

        sortedIds.forEach { id ->
            val button = buttonMap[id]
            if (button != null) {
                add(button)
                add(Box.createVerticalStrut(5))
            }
        }
    }
}
