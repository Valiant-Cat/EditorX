package editorx.gui.dialog

import editorx.core.plugin.PluginManager
import editorx.core.plugin.PluginOrigin
import editorx.core.plugin.PluginRecord
import editorx.core.plugin.PluginState
import editorx.core.plugin.loader.PluginLoaderImpl
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.*
import javax.swing.table.AbstractTableModel

class PluginManagerDialog(owner: JFrame, private val pluginManager: PluginManager) : JDialog(owner, "插件管理", true) {
    private val tableModel = PluginTableModel(pluginManager)
    private val table = JTable(tableModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        autoCreateRowSorter = true
        rowHeight = 26
        preferredScrollableViewportSize = Dimension(760, 360)
    }

    private val statusLabel = JLabel(" ").apply {
        border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    }

    private val refreshBtn = JButton("扫描").apply { addActionListener { scanPlugins() } }
    private val startBtn = JButton("启动").apply { addActionListener { startSelected() } }
    private val stopBtn = JButton("停止").apply { addActionListener { stopSelected() } }
    private val unloadBtn = JButton("卸载").apply { addActionListener { unloadSelected() } }
    private val installBtn = JButton("安装 JAR…").apply { addActionListener { installJar() } }
    private val openDirBtn = JButton("打开插件目录").apply { addActionListener { openPluginDir() } }
    private val closeBtn = JButton("关闭").apply { addActionListener { dispose() } }

    private val listener = object : PluginManager.Listener {
        override fun onPluginChanged(pluginId: String) {
            SwingUtilities.invokeLater {
                tableModel.reload()
                updateButtons()
            }
        }

        override fun onPluginUnloaded(pluginId: String) {
            SwingUtilities.invokeLater {
                tableModel.reload()
                updateButtons()
            }
        }
    }

    init {
        layout = BorderLayout()
        add(JScrollPane(table).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
            add(refreshBtn)
            add(installBtn)
            add(openDirBtn)
            add(JSeparator(SwingConstants.VERTICAL).apply { preferredSize = Dimension(1, 22) })
            add(startBtn)
            add(stopBtn)
            add(unloadBtn)
            add(Box.createHorizontalStrut(8))
            add(closeBtn)
        }
        add(actions, BorderLayout.NORTH)
        add(statusLabel, BorderLayout.SOUTH)

        setSize(860, 520)
        setLocationRelativeTo(owner)

        pluginManager.addListener(listener)
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                pluginManager.removeListener(listener)
            }
        })

        table.selectionModel.addListSelectionListener { updateButtons() }
        updateButtons()
        scanPlugins()
    }

    private fun scanPlugins() {
        val before = pluginManager.listPlugins().map { it.id }.toSet()
        pluginManager.loadAll(PluginLoaderImpl())
        tableModel.reload()
        updateButtons()

        val after = pluginManager.listPlugins()
        val newlyLoaded = after.map { it.id }.filterNot { before.contains(it) }
        if (newlyLoaded.isNotEmpty()) {
            statusLabel.text = "发现新插件：${newlyLoaded.joinToString(", ")}（未自动启动）"
        } else {
            statusLabel.text = "扫描完成"
        }
    }

    private fun selectedPluginId(): String? {
        val viewRow = table.selectedRow
        if (viewRow < 0) return null
        val modelRow = table.convertRowIndexToModel(viewRow)
        return tableModel.getPluginAt(modelRow)?.id
    }

    private fun selectedRecord(): PluginRecord? {
        val viewRow = table.selectedRow
        if (viewRow < 0) return null
        val modelRow = table.convertRowIndexToModel(viewRow)
        return tableModel.getPluginAt(modelRow)
    }

    private fun updateButtons() {
        val rec = selectedRecord()
        startBtn.isEnabled = rec != null && rec.state != PluginState.STARTED
        stopBtn.isEnabled = rec != null && rec.state == PluginState.STARTED
        unloadBtn.isEnabled = rec != null
    }

    private fun startSelected() {
        val id = selectedPluginId() ?: return
        pluginManager.startPlugin(id)
        statusLabel.text = "已启动：$id"
    }

    private fun stopSelected() {
        val id = selectedPluginId() ?: return
        pluginManager.stopPlugin(id)
        statusLabel.text = "已停止：$id"
    }

    private fun unloadSelected() {
        val rec = selectedRecord() ?: return
        val id = rec.id
        val confirm =
            JOptionPane.showConfirmDialog(
                this,
                "确定要卸载插件：${rec.name}（$id）？\n\n提示：若是 JAR 插件，同时建议从 plugins/ 目录删除对应 JAR。",
                "确认卸载",
                JOptionPane.YES_NO_OPTION
            )
        if (confirm != JOptionPane.YES_OPTION) return
        pluginManager.unloadPlugin(id)
        statusLabel.text = "已卸载：$id"
    }

    private fun installJar() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            dialogTitle = "选择插件 JAR"
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile ?: return
        if (!selected.isFile || !selected.name.endsWith(".jar", ignoreCase = true)) {
            JOptionPane.showMessageDialog(this, "请选择 .jar 文件", "提示", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val pluginDir = Path.of("plugins")
        runCatching { Files.createDirectories(pluginDir) }

        val target = pluginDir.resolve(selected.name)
        if (Files.exists(target)) {
            val overwrite =
                JOptionPane.showConfirmDialog(
                    this,
                    "插件目录已存在同名文件：${target.fileName}\n是否覆盖？",
                    "确认覆盖",
                    JOptionPane.YES_NO_OPTION
                )
            if (overwrite != JOptionPane.YES_OPTION) return
        }

        runCatching {
            Files.copy(selected.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { e ->
            JOptionPane.showMessageDialog(this, "复制失败：${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            return
        }

        val before = pluginManager.listPlugins().map { it.id }.toSet()
        pluginManager.loadAll(PluginLoaderImpl())
        val after = pluginManager.listPlugins()
        val newIds = after.map { it.id }.filterNot { before.contains(it) }
        newIds.forEach { pluginManager.startPlugin(it) }

        tableModel.reload()
        statusLabel.text =
            if (newIds.isEmpty()) "已复制到 plugins/，但未发现新的插件入口（请检查 META-INF/services 配置）"
            else "安装成功并已启动：${newIds.joinToString(", ")}"
    }

    private fun openPluginDir() {
        val dir = Path.of("plugins").toFile()
        if (!dir.exists()) dir.mkdirs()
        runCatching {
            java.awt.Desktop.getDesktop().open(dir)
        }.onFailure {
            JOptionPane.showMessageDialog(this, "无法打开目录：${dir.absolutePath}", "提示", JOptionPane.INFORMATION_MESSAGE)
        }
    }

    private class PluginTableModel(private val pluginManager: PluginManager) : AbstractTableModel() {
        private var rows: List<PluginRecord> = emptyList()

        private val columns = listOf(
            "名称",
            "ID",
            "版本",
            "来源",
            "状态",
            "路径",
            "错误",
        )

        fun reload() {
            rows = pluginManager.listPlugins()
            fireTableDataChanged()
        }

        fun getPluginAt(row: Int): PluginRecord? = rows.getOrNull(row)

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns.getOrElse(column) { "" }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val p = rows[rowIndex]
            return when (columnIndex) {
                0 -> p.name
                1 -> p.id
                2 -> p.version
                3 -> when (p.origin) {
                    PluginOrigin.CLASSPATH -> "内置"
                    PluginOrigin.JAR -> "JAR"
                }
                4 -> p.state.name
                5 -> p.source?.toString() ?: "-"
                6 -> p.lastError ?: ""
                else -> ""
            }
        }
    }

}
