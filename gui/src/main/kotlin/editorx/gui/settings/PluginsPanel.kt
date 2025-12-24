package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.plugin.PluginManager
import editorx.core.plugin.PluginOrigin
import editorx.core.plugin.PluginRecord
import editorx.core.plugin.PluginState
import editorx.core.plugin.loader.PluginLoaderImpl
import editorx.core.settings.SettingsStore
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

/**
 * 插件管理面板：左侧插件列表，右侧详情信息。
 */
class PluginsPanel(
    private val pluginManager: PluginManager,
    private val settings: SettingsStore,
) : JPanel(BorderLayout()) {

    private val disabledSet: MutableSet<String> = settings.get(DISABLED_KEY, "")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toMutableSet()
        ?: mutableSetOf()

    private val listModel = DefaultListModel<PluginRecord>()
    private val pluginList = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 12
        fixedCellHeight = 26
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val record = value as? PluginRecord
                comp.text = record?.let { "${it.name}  (${displayState(it)})" } ?: ""
                comp.border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
                return comp
            }
        }
        addListSelectionListener { updateDetails(selectedRecord()) }
    }

    private val detailName = JLabel().apply { font = font.deriveFont(java.awt.Font.BOLD, 16f) }
    private val detailId = JLabel()
    private val detailVersion = JLabel()
    private val detailOrigin = JLabel()
    private val detailState = JLabel()
    private val detailPath = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder()
        background = javax.swing.UIManager.getColor("Panel.background")
    }
    private val detailErrorLabel = JLabel()

    private val statusLabel = JLabel(" ").apply {
        border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    }

    private val refreshBtn = JButton("扫描").apply { addActionListener { scanPlugins() } }
    private val installBtn = JButton("安装 JAR…").apply { addActionListener { installJar() } }
    private val openDirBtn = JButton("打开插件目录").apply { addActionListener { openPluginDir() } }

    private val enableBtn = JButton().apply { addActionListener { enableSelected() } }
    private val disableBtn = JButton().apply { addActionListener { disableSelected() } }
    private val uninstallBtn = JButton().apply { addActionListener { uninstallSelected() } }

    private val listener = object : PluginManager.Listener {
        override fun onPluginChanged(pluginId: String) {
            SwingUtilities.invokeLater {
                reloadList()
                ensureSelection(pluginId)
            }
        }

        override fun onPluginUnloaded(pluginId: String) {
            SwingUtilities.invokeLater {
                reloadList()
                ensureSelection()
            }
        }
    }

    init {
        applyTexts()
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val leftPane = JScrollPane(pluginList).apply {
            preferredSize = Dimension(260, 0)
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, Color(0xDD, 0xDD, 0xDD))
        }

        val actionRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
            add(refreshBtn)
            add(installBtn)
            add(openDirBtn)
        }

        val controlRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            border = BorderFactory.createEmptyBorder(12, 0, 8, 0)
            add(enableBtn)
            add(disableBtn)
            add(uninstallBtn)
        }

            val detailGrid = JPanel(java.awt.GridLayout(0, 1, 0, 6)).apply {
                add(detailId)
                add(detailVersion)
                add(detailOrigin)
                add(detailState)
            }

        val detailPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 20, 12, 12)
            add(detailName, BorderLayout.NORTH)
            add(
                JPanel(BorderLayout()).apply {
                    add(detailGrid, BorderLayout.NORTH)
                    val pathBlock = JPanel(BorderLayout()).apply {
                        border = BorderFactory.createEmptyBorder(12, 0, 0, 0)
                        add(JLabel("路径 / Path:"), BorderLayout.NORTH)
                        add(detailPath, BorderLayout.CENTER)
                    }
                    add(pathBlock, BorderLayout.CENTER)
                },
                BorderLayout.CENTER
            )
            add(
                JPanel(BorderLayout()).apply {
                    add(detailErrorLabel.apply { border = BorderFactory.createEmptyBorder(8, 0, 4, 0) }, BorderLayout.NORTH)
                    add(controlRow, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH
            )
        }

        val centerPane = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(0, 12, 0, 0)
            add(detailPanel, BorderLayout.CENTER)
        }

        add(actionRow, BorderLayout.NORTH)
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, centerPane).apply {
            setDividerLocation(260)
            setResizeWeight(0.0)
            setContinuousLayout(true)
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        pluginManager.addListener(listener)
        refreshView()
    }

    fun disposePanel() {
        pluginManager.removeListener(listener)
    }

    fun refreshView() {
        applyTexts()
        reloadList()
        ensureSelection()
    }

    private fun reloadList() {
        val selectedId = selectedRecord()?.id
        listModel.clear()
        val records = pluginManager.listPlugins()
        disabledSet.clear()
        disabledSet.addAll(records.filter { it.disabled }.map { it.id })
        records.forEach { listModel.addElement(it) }
        ensureSelection(selectedId)
        statusLabel.text = if (isEnglish()) {
            "${listModel.size()} plugins"
        } else {
            "${listModel.size()} 个插件"
        }
        updateActionButtons()
    }

    private fun ensureSelection(preferredId: String? = null) {
        if (listModel.size() <= 0) {
            pluginList.clearSelection()
            updateDetails(null)
            updateActionButtons()
            return
        }
        val index = when {
            preferredId != null -> (0 until listModel.size()).firstOrNull { listModel.get(it).id == preferredId } ?: 0
            pluginList.selectedIndex >= 0 -> pluginList.selectedIndex
            else -> 0
        }
        pluginList.selectedIndex = index.coerceIn(0, listModel.size() - 1)
        updateDetails(selectedRecord())
        updateActionButtons()
    }

    private fun selectedRecord(): PluginRecord? = pluginList.selectedValue

    private fun updateDetails(record: PluginRecord?) {
        if (record == null) {
            detailName.text = if (isEnglish()) "No plugin selected" else "未选择插件"
            detailId.text = ""
            detailVersion.text = ""
            detailOrigin.text = ""
            detailState.text = ""
            detailPath.text = ""
            detailErrorLabel.text = ""
            detailErrorLabel.isVisible = false
            return
        }
        detailName.text = "${record.name} (${record.id})"
        detailId.text = "ID: ${record.id}"
        detailVersion.text = "版本 / Version: ${record.version}"
        detailOrigin.text = "来源 / Origin: ${formatOrigin(record.origin)}"
        detailState.text = "状态 / State: ${displayState(record)}"
        detailPath.text = record.source?.toString() ?: "-"
        val errorText = record.lastError?.takeIf { it.isNotBlank() }?.let {
            val prefix = if (isEnglish()) "Error: " else "错误："
            prefix + it
        } ?: ""
        detailErrorLabel.text = errorText
        detailErrorLabel.isVisible = errorText.isNotBlank()
        updateActionButtons()
    }

    private fun updateActionButtons() {
        val record = selectedRecord()
        enableBtn.isEnabled = record != null && (record.state != PluginState.STARTED || record.disabled)
        disableBtn.isEnabled = record != null && !record.disabled
        uninstallBtn.isEnabled = record != null
    }

    private fun scanPlugins() {
        val before = pluginManager.listPlugins().map { it.id }.toSet()
        pluginManager.loadAll(PluginLoaderImpl())
        reloadList()
        val after = pluginManager.listPlugins().map { it.id }
        val newlyLoaded = after.filterNot { before.contains(it) }
        statusLabel.text = if (newlyLoaded.isNotEmpty()) {
            if (isEnglish()) "Found new plugins: ${newlyLoaded.joinToString(", ")} (not started)" else "发现新插件：${newlyLoaded.joinToString(", ")}（未自动启动）"
        } else {
            if (isEnglish()) "Scan completed" else "扫描完成"
        }
    }

    private fun enableSelected() {
        val record = selectedRecord() ?: return
        pluginManager.markDisabled(record.id, false)
        disabledSet.remove(record.id)
        pluginManager.startPlugin(record.id)
        statusLabel.text = if (isEnglish()) "Enabled: ${record.id}" else "已启用：${record.id}"
        saveDisabledSet()
        reloadList()
    }

    private fun disableSelected() {
        val record = selectedRecord() ?: return
        pluginManager.markDisabled(record.id, true)
        disabledSet.add(record.id)
        statusLabel.text = if (isEnglish()) "Disabled: ${record.id}" else "已禁用：${record.id}"
        saveDisabledSet()
        reloadList()
    }

    private fun uninstallSelected() {
        val record = selectedRecord() ?: return
        val parent = SwingUtilities.getWindowAncestor(this)
        val confirm =
            JOptionPane.showConfirmDialog(
                parent,
                if (isEnglish())
                    "Remove plugin ${record.name} (${record.id})?\nIf it is a JAR plugin, also remove the file from plugins/ directory."
                else
                    "确定要卸载插件：${record.name}（${record.id}）？\n若是 JAR 插件，建议从 plugins/ 目录删除对应文件。",
                if (isEnglish()) "Confirm Removal" else "确认卸载",
                JOptionPane.YES_NO_OPTION
            )
        if (confirm != JOptionPane.YES_OPTION) return
        pluginManager.unloadPlugin(record.id)
        disabledSet.remove(record.id)
        saveDisabledSet()
        statusLabel.text = if (isEnglish()) "Removed: ${record.id}" else "已卸载：${record.id}"
        reloadList()
    }

    private fun installJar() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            dialogTitle = "选择插件 JAR"
        }
        val parent = SwingUtilities.getWindowAncestor(this)
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile ?: return
        if (!selected.isFile || !selected.name.endsWith(".jar", ignoreCase = true)) {
            JOptionPane.showMessageDialog(parent, "请选择 .jar 文件", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val pluginDir = Path.of("plugins")
        runCatching { Files.createDirectories(pluginDir) }

        val target = pluginDir.resolve(selected.name)
        if (Files.exists(target)) {
            val overwrite =
                JOptionPane.showConfirmDialog(
                    parent,
                    "插件目录已存在同名文件：${target.fileName}\n是否覆盖？",
                    "确认覆盖",
                    JOptionPane.YES_NO_OPTION
                )
            if (overwrite != JOptionPane.YES_OPTION) return
        }

        runCatching {
            Files.copy(selected.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { e ->
            JOptionPane.showMessageDialog(
                parent,
                if (isEnglish()) "Copy failed: ${e.message}" else "复制失败：${e.message}",
                if (isEnglish()) "Error" else "错误",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        val before = pluginManager.listPlugins().map { it.id }.toSet()
        pluginManager.loadAll(PluginLoaderImpl())
        val newRecords = pluginManager.listPlugins().filterNot { before.contains(it.id) }
        newRecords.forEach { pluginManager.startPlugin(it.id) }
        reloadList()
        statusLabel.text = if (newRecords.isEmpty()) {
            if (isEnglish()) "Copied to plugins/, but no new plugin entry detected (check META-INF/services)." else "已复制到 plugins/，但未发现新的插件入口（请检查 META-INF/services 配置）"
        } else {
            if (isEnglish()) "Installed and started: ${newRecords.joinToString(", ") { it.id }}" else "安装成功并已启动：${newRecords.joinToString(", ") { it.id }}"
        }
    }

    private fun openPluginDir() {
        val dir = Path.of("plugins").toFile()
        if (!dir.exists()) dir.mkdirs()
        val parent = SwingUtilities.getWindowAncestor(this)
        runCatching {
            java.awt.Desktop.getDesktop().open(dir)
        }.onFailure {
            JOptionPane.showMessageDialog(
                parent,
                if (isEnglish()) "Unable to open: ${dir.absolutePath}" else "无法打开目录：${dir.absolutePath}",
                if (isEnglish()) "Info" else "提示",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun formatOrigin(origin: PluginOrigin): String = when (origin) {
        PluginOrigin.CLASSPATH -> "内置 / Bundled"
        PluginOrigin.JAR -> "JAR"
    }

    private fun displayState(record: PluginRecord): String {
        return when {
            record.disabled -> if (isEnglish()) "Disabled" else "已禁用"
            record.state == PluginState.STARTED -> if (isEnglish()) "Enabled" else "已启用"
            record.state == PluginState.FAILED -> if (isEnglish()) "Failed" else "失败"
            else -> record.state.name
        }
    }

    private fun saveDisabledSet() {
        if (disabledSet.isEmpty()) {
            settings.remove(DISABLED_KEY)
        } else {
            settings.put(DISABLED_KEY, disabledSet.joinToString(","))
        }
        settings.sync()
    }

    private fun isEnglish(): Boolean = I18n.locale().language == Locale.ENGLISH.language

    private fun applyTexts() {
        refreshBtn.text = if (isEnglish()) "Scan" else "扫描"
        installBtn.text = if (isEnglish()) "Install JAR…" else "安装 JAR…"
        openDirBtn.text = if (isEnglish()) "Open Folder" else "打开插件目录"

        enableBtn.text = if (isEnglish()) "Enable" else "启用"
        disableBtn.text = if (isEnglish()) "Disable" else "禁用"
        uninstallBtn.text = if (isEnglish()) "Uninstall" else "卸载"
    }

    companion object {
        private const val DISABLED_KEY = "plugins.disabled"
    }
}
