package editorx.gui.ui.command

import editorx.command.CommandRegistry
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

object CommandPalette {
    fun show(owner: JFrame, registry: CommandRegistry) {
        val dialog = JDialog(owner, "命令面板", true)
        dialog.layout = BorderLayout()

        val model = DefaultListModel<CommandItem>()
        registry.all().forEach { model.addElement(CommandItem(it.id, it.title, it.description)) }

        val field = JTextField()
        val list = JList(model).apply {
            cellRenderer = object : ListCellRenderer<CommandItem> {
                private val panel = JPanel(BorderLayout())
                private val title = JLabel()
                private val desc = JLabel().apply { foreground = java.awt.Color.GRAY; font = font.deriveFont(11f) }
                override fun getListCellRendererComponent(
                    list: JList<out CommandItem>?, value: CommandItem?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): java.awt.Component {
                    title.text = value?.title ?: ""
                    desc.text = value?.description ?: value?.id ?: ""
                    panel.removeAll(); panel.add(title, BorderLayout.NORTH); panel.add(desc, BorderLayout.SOUTH)
                    panel.background = if (isSelected) list?.selectionBackground else list?.background
                    panel.foreground = if (isSelected) list?.selectionForeground else list?.foreground
                    panel.border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
                    return panel
                }
            }
        }

        field.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) { dialog.dispose(); return }
                val query = field.text.trim().lowercase()
                val filtered = registry.all().filter { it.title.lowercase().contains(query) || it.id.lowercase().contains(query) }
                val newModel = DefaultListModel<CommandItem>()
                filtered.forEach { newModel.addElement(CommandItem(it.id, it.title, it.description)) }
                list.model = newModel
                if (newModel.size() > 0) list.selectedIndex = 0
            }
        })

        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                if (e?.clickCount == 2) executeSelected(dialog, list, registry)
            }
        })

        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) executeSelected(dialog, list, registry)
                if (e.keyCode == KeyEvent.VK_ESCAPE) dialog.dispose()
            }
        })

        dialog.add(field, BorderLayout.NORTH)
        dialog.add(JScrollPane(list), BorderLayout.CENTER)
        dialog.preferredSize = Dimension(520, 360)
        dialog.pack()
        dialog.setLocationRelativeTo(owner)
        field.requestFocusInWindow()
        dialog.isVisible = true
    }

    private fun executeSelected(dialog: JDialog, list: JList<CommandItem>, registry: CommandRegistry) {
        val item = list.selectedValue ?: return
        registry.execute(item.id)
        dialog.dispose()
    }

    private data class CommandItem(val id: String, val title: String, val description: String?)
}

