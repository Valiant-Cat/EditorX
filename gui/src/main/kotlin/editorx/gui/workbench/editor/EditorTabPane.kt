package editorx.gui.workbench.editor

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class EditorTabPane : JPanel(BorderLayout()) {
    private data class TabEntry(
        var title: String,
        var icon: Icon?,
        val content: Component,
        var header: JComponent?,
        val cardId: String,
    )

    private val tabId = AtomicInteger(0)
    private val tabs = mutableListOf<TabEntry>()
    private val changeListeners = mutableListOf<ChangeListener>()

    private lateinit var tabBar: JPanel
    private lateinit var tabBarScroll: JScrollPane
    private lateinit var contentPanel: JPanel
    private var contentEnabled = true

    var selectedIndex: Int = -1
        set(value) {
            val newValue = value.coerceIn(-1, tabs.size - 1)
            if (field == newValue) return
            field = newValue
            updateSelection()
            fireStateChanged()
        }

    val tabCount: Int
        get() = tabs.size

    init {
        isOpaque = true
        tabBar = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(0, 6, 0, 6)
        }
        tabBarScroll = JScrollPane(
            tabBar,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        ).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder()
            viewport.isOpaque = false
            horizontalScrollBar.isOpaque = false
        }
        // 给tabBar添加底部padding，为滚动条留出空间（滚动条高度通常为16-20px）
        tabBar.border = BorderFactory.createEmptyBorder(0, 6, 18, 6)
        contentPanel = JPanel(java.awt.CardLayout()).apply {
            isOpaque = true
        }
        add(tabBarScroll, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)
    }

    fun addTab(title: String, icon: Icon?, component: Component, tip: String?) {
        val id = "tab-${tabId.incrementAndGet()}"
        val entry = TabEntry(title, icon, component, null, id)
        tabs.add(entry)
        if (contentEnabled) {
            contentPanel.add(component, id)
        }
        if (selectedIndex < 0) {
            selectedIndex = 0
        } else {
            updateSelection()
        }
    }

    fun removeTabAt(index: Int) {
        if (index !in tabs.indices) return
        val oldSelected = selectedIndex
        val entry = tabs.removeAt(index)
        entry.header?.let { tabBar.remove(it) }
        if (contentEnabled) {
            contentPanel.remove(entry.content)
        }
        val newSelected = when {
            tabs.isEmpty() -> -1
            index < oldSelected -> oldSelected - 1
            index == oldSelected -> oldSelected.coerceAtMost(tabs.size - 1)
            else -> oldSelected
        }
        selectedIndex = newSelected
        revalidate()
        repaint()
    }

    fun getComponentAt(index: Int): Component {
        return tabs[index].content
    }

    fun getTabComponentAt(index: Int): Component? {
        return tabs.getOrNull(index)?.header
    }

    fun setTabComponentAt(index: Int, component: Component?) {
        if (index !in tabs.indices) return
        val entry = tabs[index]
        entry.header?.let { tabBar.remove(it) }
        val header = component as? JComponent
        entry.header = header
        if (header != null) {
            val insertIndex = index.coerceAtMost(tabBar.componentCount)
            tabBar.add(header, insertIndex)
            if (index == selectedIndex) {
                updateSelection()
            }
        }
        revalidate()
        repaint()
    }

    fun setTitleAt(index: Int, title: String) {
        val entry = tabs.getOrNull(index) ?: return
        entry.title = title
    }

    fun getTitleAt(index: Int): String {
        return tabs.getOrNull(index)?.title ?: ""
    }

    fun setIconAt(index: Int, icon: Icon?) {
        val entry = tabs.getOrNull(index) ?: return
        entry.icon = icon
    }

    fun setBackgroundAt(index: Int, color: Color) {
        val header = tabs.getOrNull(index)?.header ?: return
        header.background = color
        header.isOpaque = true
        header.repaint()
    }

    fun setForegroundAt(index: Int, color: Color) {
        val header = tabs.getOrNull(index)?.header ?: return
        header.foreground = color
        header.repaint()
    }

    fun indexOfComponent(component: Component?): Int {
        if (component == null) return -1
        tabs.forEachIndexed { i, entry ->
            if (component === entry.content) return i
            if (SwingUtilities.isDescendingFrom(component, entry.content)) return i
        }
        return -1
    }

    fun indexOfTabComponent(component: Component?): Int {
        if (component == null) return -1
        tabs.forEachIndexed { i, entry ->
            val header = entry.header ?: return@forEachIndexed
            if (component === header) return i
            if (SwingUtilities.isDescendingFrom(component, header)) return i
        }
        return -1
    }

    fun indexAtLocation(x: Int, y: Int): Int {
        val point = SwingUtilities.convertPoint(this, x, y, tabBar)
        if (point.y < 0 || point.y > tabBar.height) return -1
        tabs.forEachIndexed { i, entry ->
            val header = entry.header ?: return@forEachIndexed
            val bounds = header.bounds
            if (bounds.contains(point)) return i
        }
        return -1
    }

    fun addChangeListener(listener: ChangeListener) {
        changeListeners.add(listener)
    }

    fun setContentVisible(visible: Boolean) {
        if (contentEnabled == visible) {
            contentPanel.isVisible = visible
            return
        }
        contentEnabled = visible
        if (visible) {
            add(contentPanel, BorderLayout.CENTER)
            contentPanel.removeAll()
            tabs.forEach { entry ->
                contentPanel.add(entry.content, entry.cardId)
            }
            contentPanel.isVisible = true
            updateSelection()
        } else {
            remove(contentPanel)
            contentPanel.isVisible = false
            contentPanel.removeAll()
        }
        revalidate()
        repaint()
    }

    override fun addMouseListener(l: java.awt.event.MouseListener?) {
        super.addMouseListener(l)
        if (l != null) {
            tabBar.addMouseListener(l)
        }
    }

    override fun setBackground(bg: Color?) {
        super.setBackground(bg)
        if (!this::tabBar.isInitialized) return
        tabBar.background = bg
        tabBarScroll.background = bg
        tabBarScroll.viewport.background = bg
        contentPanel.background = bg
    }

    override fun setForeground(fg: Color?) {
        super.setForeground(fg)
        if (!this::tabBar.isInitialized) return
        tabBar.foreground = fg
    }

    private fun updateSelection() {
        val idx = selectedIndex
        if (contentEnabled) {
            if (idx !in tabs.indices) {
                (contentPanel.layout as java.awt.CardLayout).show(contentPanel, "")
            } else {
                val entry = tabs[idx]
                (contentPanel.layout as java.awt.CardLayout).show(contentPanel, entry.cardId)
            }
        }
        tabs.getOrNull(idx)?.header?.let { tabBar.scrollRectToVisible(it.bounds) }
        tabBar.repaint()
    }

    private fun fireStateChanged() {
        val event = ChangeEvent(this)
        changeListeners.forEach { it.stateChanged(event) }
    }
}
