package editorx.plugins.android

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

data class StringResourceLocalesResult(
    val key: String,
    val defaultValue: String,
    val overridesByValuesDir: Map<String, String>?,
    val syncAllExistingLocalesToDefault: Boolean,
    val removedValuesDirs: Set<String>,
)

object StringResourceLocalesDialog {
    fun show(
        workspaceRoot: File,
        title: String,
        initialKey: String,
        initialDefaultValue: String?,
        hintHtml: String,
    ): StringResourceLocalesResult? {
        val keyField = JTextField(initialKey.trim().ifEmpty { "app_name" }).apply { preferredSize = Dimension(220, 26) }
        val defaultValueField = JTextField(initialDefaultValue?.trim().orEmpty()).apply { preferredSize = Dimension(360, 26) }
        val syncAll = JCheckBox("一键同步：将所有已存在语言都设置为默认名称", false)
        val onlyShowConfiguredForKey = JCheckBox("只显示已配置该 key 的多语言", true)

        // 当 key 变化时，默认名称需要跟随更新；当用户手动编辑默认名称后，不再自动覆盖。
        var userEditedDefault = false
        var lastAutoFilledKey: String? = null
        var programmaticDefaultUpdate = false

        val removedDirs = linkedSetOf<String>()
        var currentExistingDirs = emptySet<String>()
        val existingValueByDir = linkedMapOf<String, String?>()
        val manualAddedRows = linkedMapOf<String, String>()
        defaultValueField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onChange()
            override fun removeUpdate(e: DocumentEvent?) = onChange()
            override fun changedUpdate(e: DocumentEvent?) = onChange()
            private fun onChange() {
                if (programmaticDefaultUpdate) return
                val currentKey = keyField.text.trim().ifEmpty { "app_name" }
                if (lastAutoFilledKey == currentKey) {
                    // 仍是同一个 key 下用户修改，标记为手动编辑
                    userEditedDefault = true
                }
            }
        })

        val tableModel = object : DefaultTableModel(arrayOf("语言目录", "翻译"), 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = column == 1
        }
        val table = JTable(tableModel).apply {
            rowHeight = 22
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
            columnModel.getColumn(0).preferredWidth = 160
            columnModel.getColumn(0).minWidth = 140
            selectionModel.selectionMode = javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        }

        val removeSelectedButton = JButton("移除所选").apply {
            isEnabled = false
            addActionListener {
                val selected = table.selectedRows.sortedDescending()
                if (selected.isEmpty()) return@addActionListener
                for (row in selected) {
                    val dir = (tableModel.getValueAt(row, 0) as? String)?.trim().orEmpty()
                    if (dir.isNotEmpty() && dir in currentExistingDirs) {
                        removedDirs.add(dir)
                    }
                    manualAddedRows.remove(dir)
                    tableModel.removeRow(row)
                }
                isEnabled = table.selectedRowCount > 0
            }
        }
        table.selectionModel.addListSelectionListener {
            removeSelectedButton.isEnabled = table.selectedRowCount > 0
        }

        fun reloadTable(forceUpdateDefault: Boolean) {
            val key = keyField.text.trim().ifEmpty { "app_name" }
            val existing = AppInfoEditor.listStringValuesForKey(workspaceRoot, key)
            currentExistingDirs = existing.map { it.valuesDir }.toSet()
            removedDirs.clear()
            manualAddedRows.clear()
            existingValueByDir.clear()
            existing
                .filter { it.valuesDir != "values" }
                .sortedBy { it.valuesDir }
                .forEach { entry ->
                    existingValueByDir[entry.valuesDir] = entry.value
                }

            val defaultExisting = existing.firstOrNull { it.valuesDir == "values" }?.value
            if (forceUpdateDefault || (!userEditedDefault && lastAutoFilledKey != key)) {
                programmaticDefaultUpdate = true
                try {
                    defaultValueField.text = defaultExisting ?: ""
                    userEditedDefault = false
                    lastAutoFilledKey = key
                } finally {
                    programmaticDefaultUpdate = false
                }
            } else if (!defaultExisting.isNullOrBlank() && defaultValueField.text.trim().isEmpty()) {
                // 兼容：初次打开时如果没有默认值且未手动编辑，补一次
                programmaticDefaultUpdate = true
                try {
                    defaultValueField.text = defaultExisting
                    lastAutoFilledKey = key
                } finally {
                    programmaticDefaultUpdate = false
                }
            }

            tableModel.setRowCount(0)
            existingValueByDir.forEach { (dir, valueOrNull) ->
                if (dir in removedDirs) return@forEach
                if (onlyShowConfiguredForKey.isSelected && valueOrNull.isNullOrBlank()) return@forEach
                tableModel.addRow(arrayOf(dir, valueOrNull ?: ""))
            }
        }
        reloadTable(forceUpdateDefault = false)

        keyField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onChange()
            override fun removeUpdate(e: DocumentEvent?) = onChange()
            override fun changedUpdate(e: DocumentEvent?) = onChange()
            private fun onChange() {
                // key 改变：默认名称应随之刷新（覆盖为该 key 在 values 中的现有值或空）
                reloadTable(forceUpdateDefault = true)
            }
        })

        fun tableHasDir(dir: String): Boolean {
            for (i in 0 until tableModel.rowCount) {
                val existingDir = (tableModel.getValueAt(i, 0) as? String)?.trim().orEmpty()
                if (existingDir == dir) return true
            }
            return false
        }

        fun isRowConfigured(row: Int): Boolean {
            val value = (tableModel.getValueAt(row, 1) as? String)?.trim().orEmpty()
            return value.isNotEmpty()
        }

        fun applyOnlyShowConfiguredFilter() {
            if (!onlyShowConfiguredForKey.isSelected) {
                // 取消过滤：把项目里存在但当前未显示的 values* 行补回来（不覆盖已编辑内容）
                existingValueByDir.forEach { (dir, valueOrNull) ->
                    if (dir in removedDirs) return@forEach
                    if (tableHasDir(dir)) return@forEach
                    tableModel.addRow(arrayOf(dir, valueOrNull ?: ""))
                }
                return
            }

            // 启用过滤：仅隐藏“项目已有目录”中未配置该 key 的行；手动新增行不隐藏（否则无法继续编辑）
            for (row in (tableModel.rowCount - 1) downTo 0) {
                val dir = (tableModel.getValueAt(row, 0) as? String)?.trim().orEmpty()
                if (dir.isEmpty()) continue
                val isManualRow = dir !in currentExistingDirs
                if (isManualRow) continue
                if (!isRowConfigured(row)) {
                    tableModel.removeRow(row)
                }
            }
        }

        val addLocaleButton = JButton("添加翻译…").apply {
            addActionListener {
                val valueField = JTextField("").apply { preferredSize = Dimension(360, 26) }

                val allLocaleDirs = buildList {
                    val resRoot = File(workspaceRoot, "res")
                    val fromFs = resRoot.listFiles()
                        ?.filter { it.isDirectory }
                        ?.mapNotNull { it.name }
                        ?.filter { AppInfoEditor.isLocaleValuesDirName(it) && it != "values" }
                        ?: emptyList()
                    addAll(fromFs)
                    addAll(existingValueByDir.keys)
                    addAll(manualAddedRows.keys)
                }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != "values" }
                    .distinct()
                    .sorted()

                val dirCombo = JComboBox(allLocaleDirs.toTypedArray()).apply {
                    isEditable = true
                    preferredSize = Dimension(220, 26)
                }

                val form = JPanel(GridBagLayout()).apply {
                    border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
                    val c = GridBagConstraints().apply {
                        insets = Insets(6, 6, 6, 6)
                        fill = GridBagConstraints.HORIZONTAL
                        weightx = 1.0
                        gridx = 0
                        gridy = 0
                        anchor = GridBagConstraints.WEST
                    }

                    fun row(label: String, comp: java.awt.Component) {
                        c.gridx = 0
                        c.weightx = 0.0
                        c.fill = GridBagConstraints.NONE
                        add(JLabel(label), c)
                        c.gridx = 1
                        c.weightx = 1.0
                        c.fill = GridBagConstraints.HORIZONTAL
                        add(comp, c)
                        c.gridy++
                    }

                    row("语言目录", dirCombo)
                    row("翻译内容", valueField)
                    c.gridx = 1
                    c.weightx = 1.0
                    c.fill = GridBagConstraints.HORIZONTAL
                    add(
                        JLabel("<html><small>示例：values-en、values-zh-rCN、values-b+zh+Hans+CN</small></html>"),
                        c
                    )
                }

                val option = JOptionPane.showConfirmDialog(
                    null,
                    form,
                    "添加翻译",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
                )
                if (option != JOptionPane.OK_OPTION) return@addActionListener

                val normalized = (dirCombo.editor.item as? String ?: dirCombo.selectedItem as? String)
                    ?.trim()
                    .orEmpty()
                val translation = valueField.text.trim()
                if (normalized.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "请填写语言目录（例如 values-en）", "提示", JOptionPane.WARNING_MESSAGE)
                    return@addActionListener
                }
                if (normalized == "values") {
                    JOptionPane.showMessageDialog(null, "默认语言请在“默认语言”输入框中设置", "提示", JOptionPane.WARNING_MESSAGE)
                    return@addActionListener
                }
                if (!AppInfoEditor.isLocaleValuesDirName(normalized)) {
                    JOptionPane.showMessageDialog(null, "语言目录格式不合法：$normalized", "提示", JOptionPane.WARNING_MESSAGE)
                    return@addActionListener
                }
                if (translation.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "请填写翻译内容", "提示", JOptionPane.WARNING_MESSAGE)
                    return@addActionListener
                }

                if (tableHasDir(normalized)) return@addActionListener

                tableModel.addRow(arrayOf(normalized, translation))
                manualAddedRows[normalized] = translation
            }
        }

        onlyShowConfiguredForKey.addActionListener {
            applyOnlyShowConfiguredFilter()
        }

        val content = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
            add(
                buildForm(
                    keyField = keyField,
                    defaultNameField = defaultValueField,
                    syncAll = syncAll,
                    table = table,
                    addLocaleButton = addLocaleButton,
                    removeSelectedButton = removeSelectedButton,
                    onlyShowConfiguredForKey = onlyShowConfiguredForKey,
                ),
                BorderLayout.CENTER
            )
            add(JLabel(hintHtml), BorderLayout.SOUTH)
        }

        val option = JOptionPane.showConfirmDialog(
            null,
            content,
            title,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )
        if (option != JOptionPane.OK_OPTION) return null

        val key = keyField.text.trim().ifEmpty { "app_name" }
        val defaultValue = defaultValueField.text.trim()
        if (defaultValue.isEmpty()) {
            JOptionPane.showMessageDialog(null, "请填写默认名称（values）", "提示", JOptionPane.WARNING_MESSAGE)
            return null
        }

        val overrides = if (syncAll.isSelected) {
            null
        } else {
            val map = linkedMapOf<String, String>()
            map["values"] = defaultValue
            for (i in 0 until tableModel.rowCount) {
                val dir = (tableModel.getValueAt(i, 0) as? String)?.trim().orEmpty()
                val value = (tableModel.getValueAt(i, 1) as? String)?.trim().orEmpty()
                if (dir.isNotEmpty() && value.isNotEmpty()) map[dir] = value
            }
            map
        }

        return StringResourceLocalesResult(
            key = key,
            defaultValue = defaultValue,
            overridesByValuesDir = overrides,
            syncAllExistingLocalesToDefault = syncAll.isSelected,
            removedValuesDirs = removedDirs.toSet(),
        )
    }

    private fun buildForm(
        keyField: JTextField,
        defaultNameField: JTextField,
        syncAll: JCheckBox,
        table: JTable,
        addLocaleButton: JButton,
        removeSelectedButton: JButton,
        onlyShowConfiguredForKey: JCheckBox,
    ): JPanel {
        val grid = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
        }

        fun row(label: String, comp: java.awt.Component) {
            c.gridx = 0
            c.weightx = 0.0
            c.fill = GridBagConstraints.NONE
            grid.add(JLabel(label), c)

            c.gridx = 1
            c.weightx = 1.0
            c.fill = GridBagConstraints.HORIZONTAL
            grid.add(comp, c)
            c.gridy++
        }

        row("key（@string）", keyField)

        row("默认语言", defaultNameField)

        c.gridx = 1
        c.weightx = 1.0
        c.fill = GridBagConstraints.HORIZONTAL
        grid.add(syncAll, c)
        c.gridy++

        // 多语言配置：与其他字段保持一致的“左标题 + 右内容”布局
        val scroll = JScrollPane(table).apply { preferredSize = Dimension(520, 180) }
        val localesPanel = JPanel(BorderLayout()).apply {
            add(scroll, BorderLayout.CENTER)
            val actionRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 6)).apply {
                isOpaque = false
                add(addLocaleButton)
                add(javax.swing.Box.createHorizontalStrut(8))
                add(removeSelectedButton)
            }
            val filterRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(onlyShowConfiguredForKey)
            }
            add(
                JPanel().apply {
                    isOpaque = false
                    layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                    add(filterRow)
                    add(actionRow)
                },
                BorderLayout.SOUTH
            )
        }

        c.gridx = 0
        c.weightx = 0.0
        c.weighty = 0.0
        c.fill = GridBagConstraints.NONE
        grid.add(JLabel("多语言配置"), c)

        c.gridx = 1
        c.weightx = 1.0
        c.weighty = 1.0
        c.fill = GridBagConstraints.BOTH
        grid.add(localesPanel, c)
        c.gridy++
        c.weighty = 0.0
        c.fill = GridBagConstraints.HORIZONTAL

        return grid
    }
}
