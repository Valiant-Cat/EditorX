package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.plugin.PluginManager
import editorx.core.gui.GuiContext
import editorx.gui.main.MainWindow
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

class SettingsDialog(
    owner: MainWindow,
    private val environment: GuiContext,
    private val pluginManager: PluginManager,
    private val defaultSection: Section = Section.APPEARANCE,
) : JDialog(owner, if (isEnglish()) "Preferences" else "设置", true) {

    enum class Section { APPEARANCE, KEYMAP, PLUGINS, CACHE }

    private data class SectionItem(
        val section: Section,
        val zh: String,
        val en: String,
    ) {
        fun label(): String = if (isEnglish()) en else zh
    }

    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout).apply { isOpaque = false }

    private val appearancePanel = AppearancePanel(environment.settings)
    private val keymapPanel = KeymapPanel()
    private val pluginsPanel = PluginsPanel(pluginManager, environment.settings)
    private val cachePanel = CachePanel(environment)

    private val listModel = DefaultListModel<SectionItem>().apply {
        addElement(SectionItem(Section.APPEARANCE, "外观", "Appearance"))
        addElement(SectionItem(Section.KEYMAP, "快捷键", "Keymap"))
        addElement(SectionItem(Section.PLUGINS, "插件", "Plugins"))
        addElement(SectionItem(Section.CACHE, "缓存", "Cache"))
    }

    private val navigation = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = 28
        border = BorderFactory.createEmptyBorder()
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val item = value as? SectionItem
                c.text = item?.label() ?: ""
                c.border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
                return c
            }
        }
        addListSelectionListener {
            val item = selectedValue ?: return@addListSelectionListener
            showSection(item.section)
        }
    }

    private val i18nListener = {
        SwingUtilities.invokeLater {
            title = if (isEnglish()) "Preferences" else "设置"
            navigation.repaint()
            appearancePanel.refresh()
            keymapPanel.refresh()
            cachePanel.refresh()
        }
    }

    init {
        contentPanel.add(appearancePanel, Section.APPEARANCE.name)
        contentPanel.add(keymapPanel, Section.KEYMAP.name)
        contentPanel.add(pluginsPanel, Section.PLUGINS.name)
        contentPanel.add(cachePanel, Section.CACHE.name)

        layout = BorderLayout()
        add(buildBody(), BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)

        size = Dimension(940, 640)
        setLocationRelativeTo(owner)
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        navigation.selectedIndex = listModel.elements().asSequence().indexOfFirst { it.section == defaultSection }
            .takeIf { it >= 0 } ?: 0

        I18n.addListener(i18nListener)
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                pluginsPanel.disposePanel()
                I18n.removeListener(i18nListener)
            }
        })
    }

    private fun buildBody(): JComponent {
        val navigationPane = JPanel(BorderLayout()).apply {
            background = Color.WHITE
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Color(0xD0, 0xD0, 0xD0)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
            )
            val title = JLabel(if (isEnglish()) "Preferences" else "设置项").apply {
                font = font.deriveFont(Font.BOLD, 13f)
                border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
            }
            val scroll = JScrollPane(navigation).apply {
                border = BorderFactory.createMatteBorder(1, 1, 1, 1, Color(0xDD, 0xDD, 0xDD))
                background = Color.WHITE
                viewport.background = Color.WHITE
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }
            add(title, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }

        val contentWrapper = JPanel(BorderLayout()).apply {
            background = Color(0xF3, 0xF4, 0xF6)
            border = BorderFactory.createEmptyBorder(16, 16, 16, 24)
            val inner = JPanel(BorderLayout()).apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 1, 1, Color(0xD0, 0xD0, 0xD0)),
                    BorderFactory.createEmptyBorder(24, 28, 28, 28)
                )
                background = Color.WHITE
                add(contentPanel, BorderLayout.CENTER)
            }
            add(inner, BorderLayout.CENTER)
        }

        return JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationPane, contentWrapper).apply {
            setDividerLocation(240)
            setResizeWeight(0.0)
            isOneTouchExpandable = false
            setContinuousLayout(true)
            border = BorderFactory.createEmptyBorder()
        }
    }

    private fun buildFooter(): JComponent {
        val resetPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6)).apply {
            isOpaque = false
            add(JButton(if (isEnglish()) "Reset" else "重置").apply {
                isFocusable = false
                addActionListener { onResetPressed() }
            })
        }
        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 12, 6)).apply {
            isOpaque = false
            add(JButton(if (isEnglish()) "Cancel" else "取消").apply {
                addActionListener { dispose() }
            })
            add(JButton(if (isEnglish()) "Confirm" else "确定").apply {
                addActionListener {
                    environment.settings.sync()
                    dispose()
                }
            })
        }
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color(0xD0, 0xD0, 0xD0)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
            )
            isOpaque = false
            add(resetPanel, BorderLayout.WEST)
            add(actionPanel, BorderLayout.EAST)
        }
    }

    private fun showSection(section: Section) {
        cardLayout.show(contentPanel, section.name)
        when (section) {
            Section.APPEARANCE -> appearancePanel.refresh()
            Section.KEYMAP -> keymapPanel.refresh()
            Section.PLUGINS -> pluginsPanel.refreshView()
            Section.CACHE -> cachePanel.refresh()
        }
    }

    private fun onResetPressed() {
        // 仅恢复语言为默认简体中文，其他设置保留。后续可扩展更多重置选项。
        appearancePanel.resetToDefault()
        navigation.repaint()
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    companion object {
        private fun isEnglish(): Boolean = I18n.locale().language == Locale.ENGLISH.language
    }
}
