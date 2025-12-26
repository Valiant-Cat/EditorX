package editorx.gui.settings

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.PluginManager
import editorx.core.plugin.PluginOrigin
import editorx.core.plugin.PluginRecord
import editorx.core.plugin.PluginState
import editorx.core.plugin.loader.PluginLoaderImpl
import editorx.core.store.Store
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
    private val settings: Store,
) : JPanel(BorderLayout()) {

    private val pluginSplitPane: JSplitPane
    
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

    private val installBtn = JButton("安装插件").apply { addActionListener { installPlugin() } }
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
        
        // 在插件列表区域禁用对话框的拖拽功能，避免与插件面板内部的拖拽操作冲突
        // 注意：这不会影响对话框标题栏的拖拽功能
        pluginList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                // 在插件列表区域，阻止事件传播到对话框，避免触发对话框拖拽
                if (e.source == pluginList) {
                    e.consume()
                }
            }
        })
        pluginList.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                // 在插件列表区域拖拽时，阻止事件传播到对话框
                if (e.source == pluginList) {
                    e.consume()
                }
            }
        })

        val leftPane = JScrollPane(pluginList).apply {
            // 只设置最小宽度，不设置 preferredSize，让 JSplitPane 可以自由调整
            // 这样向左拖拽后，可以向右拖拽回来
            minimumSize = Dimension(200, 0)
            // 不设置 preferredSize，避免限制拖拽范围
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, Color(0xDD, 0xDD, 0xDD))
        }

        val actionRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
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
                    add(
                        detailErrorLabel.apply { border = BorderFactory.createEmptyBorder(8, 0, 4, 0) },
                        BorderLayout.NORTH
                    )
                    add(controlRow, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH
            )
        }

        val centerPane = JPanel(BorderLayout()).apply {
            // 关键：显式设置右侧面板最小宽度，避免默认 minSize=preferredSize 随布局变大而“锁死”
            // JSplitPane 的 maximumDividerLocation 依赖 rightComponent.minimumSize，
            // 如果它跟随当前宽度增长，就会出现向左拖后无法再向右拖回的问题。
            minimumSize = Dimension(0, 0)
            border = BorderFactory.createEmptyBorder(0, 12, 0, 0)
            add(detailPanel, BorderLayout.CENTER)
        }

        pluginSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, centerPane).apply {
            setDividerLocation(260)
            // 使用 0.0 的 resizeWeight 意味着左侧面板大小固定，右侧面板会随 JSplitPane 大小变化
            setResizeWeight(0.0)
            setContinuousLayout(true)
            border = BorderFactory.createEmptyBorder()
        }
        
        // 监听父容器大小变化，确保 JSplitPane 可以正常调整 dividerLocation
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) {
                // 当父容器大小变化时，确保 dividerLocation 在合理范围内
                SwingUtilities.invokeLater {
                    val currentLocation = pluginSplitPane.dividerLocation
                    val minLocation = pluginSplitPane.minimumDividerLocation
                    val maxLocation = pluginSplitPane.maximumDividerLocation
                    
                    // 如果当前位置超出范围，调整到合理位置
                    if (currentLocation < minLocation || currentLocation > maxLocation) {
                        val targetLocation = currentLocation.coerceIn(minLocation, maxLocation)
                        if (targetLocation != currentLocation) {
                            pluginSplitPane.dividerLocation = targetLocation
                        }
                    }
                }
            }
        })
        
        // 完全移除所有事件 consume，让 JSplitPane 完全正常工作
        // 嵌套拖拽冲突的解决完全在 SettingsDialog 层面处理
        
        add(actionRow, BorderLayout.NORTH)
        add(pluginSplitPane, BorderLayout.CENTER)
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
    
    /**
     * 恢复 PluginsPanel 的 JSplitPane 到合理的位置
     */
    private fun restorePluginSplitPaneLocation() {
        val currentLocation = pluginSplitPane.dividerLocation
        val maxLocation = pluginSplitPane.maximumDividerLocation
        // 如果当前位置明显小于最大位置（可能之前被挤压过），尝试恢复到更合理的位置
        if (currentLocation < maxLocation && currentLocation < 400 && maxLocation >= 400) {
            // 恢复到至少 400 像素，或者最大位置，取较小值
            val targetLocation = 400.coerceAtMost(maxLocation)
            if (targetLocation > currentLocation) {
                pluginSplitPane.dividerLocation = targetLocation
            }
        }
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
        // 内置插件（CLASSPATH）不可卸载
        uninstallBtn.isEnabled = record != null && record.origin != PluginOrigin.CLASSPATH
    }

    private fun scanPlugins() {
        val before = pluginManager.listPlugins().map { it.id }.toSet()
        pluginManager.loadAll(PluginLoaderImpl())
        reloadList()
        val after = pluginManager.listPlugins().map { it.id }
        val newlyLoaded = after.filterNot { before.contains(it) }
        statusLabel.text = if (newlyLoaded.isNotEmpty()) {
            if (isEnglish()) "Found new plugins: ${newlyLoaded.joinToString(", ")} (not started)" else "发现新插件：${
                newlyLoaded.joinToString(
                    ", "
                )
            }（未自动启动）"
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
        
        // 内置插件不可卸载
        if (record.origin == PluginOrigin.CLASSPATH) {
            val parent = SwingUtilities.getWindowAncestor(this)
            JOptionPane.showMessageDialog(
                parent,
                if (isEnglish())
                    "Built-in plugins cannot be uninstalled.\nPlugin: ${record.name} (${record.id})"
                else
                    "内置插件不可卸载。\n插件：${record.name}（${record.id}）",
                if (isEnglish()) "Cannot Uninstall" else "无法卸载",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        
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
        
        val success = pluginManager.unloadPlugin(record.id)
        if (success) {
            disabledSet.remove(record.id)
            saveDisabledSet()
            statusLabel.text = if (isEnglish()) "Removed: ${record.id}" else "已卸载：${record.id}"
            reloadList()
        } else {
            JOptionPane.showMessageDialog(
                parent,
                if (isEnglish())
                    "Failed to uninstall plugin: ${record.name} (${record.id})"
                else
                    "卸载插件失败：${record.name}（${record.id}）",
                if (isEnglish()) "Error" else "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun installPlugin() {
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
            if (isEnglish()) "Installed and started: ${newRecords.joinToString(", ") { it.id }}" else "安装成功并已启动：${
                newRecords.joinToString(
                    ", "
                ) { it.id }
            }"
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
            record.disabled -> I18n.translate(I18nKeys.Settings.PLUGIN_STATE_DISABLED)
            record.state == PluginState.STARTED -> I18n.translate(I18nKeys.Settings.PLUGIN_STATE_ENABLED)
            record.state == PluginState.FAILED -> I18n.translate(I18nKeys.Settings.PLUGIN_STATE_FAILED)
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
        installBtn.text = I18n.translate(I18nKeys.Action.INSTALL_PLUGIN)
        openDirBtn.text = I18n.translate(I18nKeys.Action.OPEN_PLUGINS_FOLDER)

        enableBtn.text = I18n.translate(I18nKeys.Action.ENABLE)
        disableBtn.text = I18n.translate(I18nKeys.Action.DISABLE)
        uninstallBtn.text = I18n.translate(I18nKeys.Action.UNINSTALL)
    }

    companion object {
        private const val DISABLED_KEY = "plugins.disabled"
    }
}
