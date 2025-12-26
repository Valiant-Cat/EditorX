package editorx.gui.search

import editorx.gui.ThemeManager
import editorx.gui.main.MainWindow
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

/**
 * 全局搜索弹窗（参考 IDEA 和 Jadx）
 * 支持按类型搜索：类、方法、字段、代码、资源
 */
class SearchDialog(
    owner: java.awt.Window,
    private val mainWindow: MainWindow
) : JDialog(owner, "全局搜索", java.awt.Dialog.ModalityType.APPLICATION_MODAL) {

    private companion object {
        private const val MAX_RESULTS = 5000
        private const val MAX_FILE_BYTES = 5L * 1024 * 1024 // 5MB
        private val SKIP_DIR_NAMES = setOf(".git", ".gradle", "build", "out", ".idea", "dist")
        private val BINARY_EXTS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "ico",
            "so", "dll", "dylib",
            "jar", "apk", "dex", "class",
            "arsc", "ttf", "otf", "mp3", "mp4", "wav",
            "keystore", "jks",
        )
    }

    // 搜索选项复选框
    private val searchClass = JCheckBox("类", true)
    private val searchMethod = JCheckBox("方法", true)
    private val searchField = JCheckBox("字段", true)
    private val searchCode = JCheckBox("代码", true)
    private val searchResource = JCheckBox("资源", true)

    private val queryField = JTextField()
    private val searchButton = JButton("搜索")
    private val stopButton = JButton("停止").apply { isEnabled = false }

    private val resultModel = DefaultListModel<SearchMatch>()
    private val resultList = JList(resultModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        val rootPath = workspaceRoot()?.toPath()
        cellRenderer = ResultRenderer(rootPath)
        fixedCellHeight = 0
    }

    private val statusLabel = JLabel(" ").apply {
        foreground = Color(0x66, 0x66, 0x66)
        font = font.deriveFont(Font.PLAIN, 12f)
        border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    }

    private var worker: SearchWorker? = null
    private var searchToken: Int = 0

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isResizable = true
        setSize(800, 600)
        setLocationRelativeTo(owner)

        layout = BorderLayout()
        background = Color.WHITE

        add(buildTopPanel(), BorderLayout.NORTH)
        add(JScrollPane(resultList).apply {
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, ThemeManager.separator)
            background = Color.WHITE
            viewport.background = Color.WHITE
        }, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        installListeners()
        updateStatusIdle()
    }

    fun showDialog() {
        isVisible = true
        SwingUtilities.invokeLater {
            queryField.requestFocusInWindow()
            queryField.selectAll()
        }
    }

    private fun buildTopPanel(): JPanel {
        // 第一行：搜索输入框和按钮
        val row1 = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(JLabel("搜索：").apply { 
                foreground = Color(0x44, 0x44, 0x44)
                preferredSize = Dimension(50, 28)
            }, BorderLayout.WEST)
            add(queryField, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                    isOpaque = false
                    add(searchButton)
                    add(stopButton)
                },
                BorderLayout.EAST,
            )
        }

        // 第二行：搜索类型复选框
        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 8, 8, 8)
            add(JLabel("搜索定义：").apply { 
                foreground = Color(0x44, 0x44, 0x44)
            })
            add(searchClass)
            add(searchMethod)
            add(searchField)
            add(searchCode)
            add(searchResource)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(row1, BorderLayout.NORTH)
            add(row2, BorderLayout.SOUTH)
        }
    }

    private fun installListeners() {
        searchButton.addActionListener { startSearch() }
        stopButton.addActionListener { stopSearch() }

        queryField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> startSearch()
                    KeyEvent.VK_ESCAPE -> dispose()
                }
            }
        })

        resultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && e.button == java.awt.event.MouseEvent.BUTTON1) {
                    navigateToSelected()
                }
            }
        })
        resultList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> navigateToSelected()
                    KeyEvent.VK_ESCAPE -> dispose()
                }
            }
        })
    }

    private fun startSearch() {
        val query = queryField.text?.trim().orEmpty()
        if (query.isBlank()) {
            statusLabel.text = "请输入要搜索的内容"
            return
        }

        val root = workspaceRoot()
        if (root == null) {
            statusLabel.text = "请先打开文件夹（工作区），再进行全局搜索"
            return
        }

        // 检查是否至少选择了一个搜索类型
        if (!searchClass.isSelected && !searchMethod.isSelected && !searchField.isSelected 
            && !searchCode.isSelected && !searchResource.isSelected) {
            statusLabel.text = "请至少选择一个搜索类型"
            return
        }

        stopSearch()
        resultModel.clear()

        val token = ++searchToken
        val options = SearchOptions(
            query = query,
            searchClass = searchClass.isSelected,
            searchMethod = searchMethod.isSelected,
            searchField = searchField.isSelected,
            searchCode = searchCode.isSelected,
            searchResource = searchResource.isSelected,
        )

        val newWorker = SearchWorker(
            root,
            options,
            onProgress = onProgress@{ filesScanned, matches ->
                if (token != searchToken) return@onProgress
                statusLabel.text = "已扫描 $filesScanned 个文件，找到 $matches 条结果"
            },
            onMatch = onMatch@{ match ->
                if (token != searchToken) return@onMatch
                resultModel.addElement(match)
            },
            onDone = onDone@{ summary ->
                if (token != searchToken) return@onDone
                statusLabel.text = summary
                stopButton.isEnabled = false
                searchButton.isEnabled = true
                worker = null
            }
        )
        worker = newWorker
        stopButton.isEnabled = true
        searchButton.isEnabled = false
        statusLabel.text = "搜索中…"
        newWorker.execute()
    }

    private fun stopSearch() {
        searchToken++
        worker?.cancel(true)
        worker = null
        stopButton.isEnabled = false
        searchButton.isEnabled = true
    }

    private fun navigateToSelected() {
        val match = resultList.selectedValue ?: return
        mainWindow.editor.openFileAndSelect(match.file, match.line, match.column, match.length)
        dispose()
    }

    private fun workspaceRoot(): File? = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()

    private fun updateStatusIdle() {
        statusLabel.text = "输入搜索内容并按 Enter 或点击搜索按钮"
    }

    private data class SearchOptions(
        val query: String,
        val searchClass: Boolean,
        val searchMethod: Boolean,
        val searchField: Boolean,
        val searchCode: Boolean,
        val searchResource: Boolean,
    )

    private class SearchWorker(
        private val root: File,
        private val options: SearchOptions,
        private val onProgress: (Int, Int) -> Unit,
        private val onMatch: (SearchMatch) -> Unit,
        private val onDone: (String) -> Unit,
    ) : SwingWorker<String, SearchMatch>() {

        private var filesScanned: Int = 0
        private var matches: Int = 0
        private var hitLimit: Boolean = false
        private val rootPath: Path = root.toPath()

        override fun doInBackground(): String {
            val startedAt = System.currentTimeMillis()
            val queryLower = options.query.lowercase()

            Files.walk(rootPath).use { stream ->
                val iter = stream.iterator()
                while (iter.hasNext() && !isCancelled) {
                    val path = iter.next()
                    if (!Files.isRegularFile(path)) continue
                    if (shouldSkip(path)) continue

                    val file = path.toFile()
                    if (!file.canRead()) continue
                    if (file.length() > MAX_FILE_BYTES) continue
                    if (isLikelyBinary(file)) continue

                    filesScanned++
                    if (filesScanned % 50 == 0) {
                        publishProgress()
                    }

                    searchFile(file, queryLower)
                    if (matches >= MAX_RESULTS) {
                        hitLimit = true
                        break
                    }
                }
            }

            val costMs = System.currentTimeMillis() - startedAt
            val base = "完成：扫描 $filesScanned 个文件，找到 $matches 条结果，用时 ${costMs}ms"
            return if (hitLimit) "$base（结果已达上限 ${MAX_RESULTS}）" else base
        }

        override fun process(chunks: MutableList<SearchMatch>) {
            chunks.forEach(onMatch)
        }

        override fun done() {
            if (isCancelled) {
                onDone("已停止：扫描 $filesScanned 个文件，找到 $matches 条结果")
                return
            }
            runCatching { get() }
                .onSuccess(onDone)
                .onFailure { onDone("搜索失败：${it.message ?: "未知错误"}") }
        }

        private fun publishProgress() {
            SwingUtilities.invokeLater { onProgress(filesScanned, matches) }
        }

        private fun searchFile(file: File, queryLower: String) {
            val fileExt = file.extension.lowercase()
            val fileName = file.name.lowercase()

            // 根据文件类型和搜索选项决定是否搜索
            val isJavaFile = fileExt in setOf("java", "kt", "kts")
            val isSmaliFile = fileExt == "smali"
            val isXmlFile = fileExt == "xml"
            val isResourceFile = fileExt in setOf("xml", "png", "jpg", "jpeg", "gif", "webp", "ico", "ttf", "otf")

            // 检查是否应该搜索此文件
            var shouldSearch = false
            if (options.searchCode) {
                shouldSearch = true // 代码搜索包含所有文本文件
            }
            if (options.searchClass && (isJavaFile || isSmaliFile)) {
                shouldSearch = true
            }
            if (options.searchMethod && (isJavaFile || isSmaliFile)) {
                shouldSearch = true
            }
            if (options.searchField && (isJavaFile || isSmaliFile)) {
                shouldSearch = true
            }
            if (options.searchResource && isResourceFile) {
                shouldSearch = true
            }

            if (!shouldSearch) return

            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            InputStreamReader(FileInputStream(file), decoder).use { reader ->
                reader.buffered().readLinesWithLineNumber { lineNo, line ->
                    if (isCancelled) return@readLinesWithLineNumber false
                    
                    val lineLower = line.lowercase()
                    if (!lineLower.contains(queryLower)) return@readLinesWithLineNumber true

                    // 根据搜索类型过滤结果
                    val matchType = detectMatchType(line, queryLower, isJavaFile, isSmaliFile, isXmlFile)
                    if (matchType == null) return@readLinesWithLineNumber true

                    val col = lineLower.indexOf(queryLower)
                    if (col >= 0 && matches < MAX_RESULTS) {
                        publish(
                            SearchMatch(
                                file = file,
                                line = lineNo,
                                column = col,
                                length = queryLower.length,
                                preview = line.take(200),
                            )
                        )
                        matches++
                    }
                    true
                }
            }
        }

        private fun detectMatchType(
            line: String,
            queryLower: String,
            isJavaFile: Boolean,
            isSmaliFile: Boolean,
            isXmlFile: Boolean
        ): String? {
            val lineLower = line.lowercase().trim()

            if (isJavaFile || isSmaliFile) {
                // 类定义
                if (options.searchClass) {
                    if (lineLower.contains("class $queryLower") || 
                        lineLower.contains("interface $queryLower") ||
                        lineLower.contains("enum $queryLower")) {
                        return "class"
                    }
                }
                // 方法定义
                if (options.searchMethod) {
                    val escapedQuery = Pattern.quote(queryLower)
                    if (lineLower.contains("fun $queryLower") || 
                        lineLower.contains("function $queryLower") ||
                        Pattern.compile("\\b$escapedQuery\\s*\\(").matcher(lineLower).find()) {
                        return "method"
                    }
                }
                // 字段定义
                if (options.searchField) {
                    val escapedQuery = Pattern.quote(queryLower)
                    if (Pattern.compile("\\b(val|var|private|public|protected|static)\\s+$escapedQuery\\b").matcher(lineLower).find()) {
                        return "field"
                    }
                }
            }

            if (isXmlFile && options.searchResource) {
                return "resource"
            }

            // 代码搜索（匹配所有包含查询的文本）
            if (options.searchCode) {
                return "code"
            }

            return null
        }

        private fun shouldSkip(path: Path): Boolean {
            val name = path.fileName.toString()
            return SKIP_DIR_NAMES.contains(name) || name.startsWith(".")
        }

        private fun isLikelyBinary(file: File): Boolean {
            val ext = file.extension.lowercase()
            return BINARY_EXTS.contains(ext)
        }
    }

    private class ResultRenderer(
        private val workspaceRoot: Path?
    ) : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val match = value as? SearchMatch ?: return comp

            val root = workspaceRoot
            val relPath = if (root != null) {
                try {
                    root.relativize(match.file.toPath()).toString()
                } catch (_: Exception) {
                    match.file.name
                }
            } else {
                match.file.name
            }

            comp.text = "<html><b>$relPath</b>:${match.line}:${match.column + 1} - ${match.preview}</html>"
            comp.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            return comp
        }
    }
}

// 扩展函数：按行读取文件并带行号
private fun java.io.BufferedReader.readLinesWithLineNumber(
    action: (Int, String) -> Boolean
) {
    var lineNo = 1
    forEachLine { line ->
        if (!action(lineNo, line)) return@forEachLine
        lineNo++
    }
}

