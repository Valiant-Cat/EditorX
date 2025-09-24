package editorx.gui.ui.panel

import editorx.gui.ui.MainWindow
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.*

class Panel(private val mainWindow: MainWindow) : JPanel() {
    private val tabbedPane = JTabbedPane()
    private val viewMap = mutableMapOf<String, JComponent>()

    init { setupPanel() }

    private fun setupPanel() {
        layout = BorderLayout()
        tabbedPane.apply {
            tabPlacement = JTabbedPane.BOTTOM
            tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
            background = Color.WHITE
        }
        add(tabbedPane, BorderLayout.CENTER)
        createDefaultViews()
    }

    private fun createDefaultViews() {
        val outputPanel = JScrollPane().apply {
            val textArea = JTextArea().apply {
                isEditable = false
                font = java.awt.Font("Consolas", java.awt.Font.PLAIN, 12)
                background = Color.BLACK
                foreground = Color.GREEN
                text = "EditorX 输出面板\n等待输出...\n"
            }
            setViewportView(textArea)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }
        registerView("输出", null, outputPanel)

        val problemsPanel = JScrollPane().apply {
            val listModel = DefaultListModel<String>()
            val list = JList(listModel).apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                font = java.awt.Font("Consolas", java.awt.Font.PLAIN, 12)
            }
            listModel.addElement("没有发现问题")
            setViewportView(list)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        }
        registerView("问题", null, problemsPanel)

        val terminalPanel = JPanel().apply {
            layout = BorderLayout()
            val textArea = JTextArea().apply {
                isEditable = true
                font = java.awt.Font("Consolas", java.awt.Font.PLAIN, 12)
                background = Color.BLACK
                foreground = Color.WHITE
                text = "EditorX 终端\n$ "
            }
            add(JScrollPane(textArea).apply {
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
            }, BorderLayout.CENTER)
        }
        registerView("终端", null, terminalPanel)
    }

    fun registerView(title: String, icon: Icon?, component: JComponent) {
        viewMap[title] = component
        tabbedPane.addTab(title, icon, component)
    }

    fun showView(title: String) {
        for (i in 0 until tabbedPane.tabCount) {
            if (tabbedPane.getTitleAt(i) == title) { tabbedPane.selectedIndex = i; return }
        }
    }

    fun getCurrentViewTitle(): String = if (tabbedPane.selectedIndex >= 0) tabbedPane.getTitleAt(tabbedPane.selectedIndex) else ""

    fun removeView(title: String) {
        for (i in 0 until tabbedPane.tabCount) {
            if (tabbedPane.getTitleAt(i) == title) {
                tabbedPane.removeTabAt(i)
                viewMap.remove(title)
                return
            }
        }
    }

    fun hasView(title: String): Boolean = viewMap.containsKey(title)
    fun getView(title: String): JComponent? = viewMap[title]
    fun getAllViewTitles(): Set<String> = viewMap.keys.toSet()

    fun clearViews() {
        val defaultViews = setOf("输出", "问题", "终端")
        val viewsToRemove = viewMap.keys.filter { it !in defaultViews }
        viewsToRemove.forEach { removeView(it) }
    }
}
