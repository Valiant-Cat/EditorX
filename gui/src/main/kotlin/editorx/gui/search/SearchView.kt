package editorx.gui.search

import editorx.gui.ThemeManager
import editorx.gui.main.MainWindow
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

/**
 * 全局搜索视图（参考 jadx：输入关键字 -> 结果列表 -> 双击跳转）。
 */
class SearchView(private val mainWindow: MainWindow) : JPanel(BorderLayout()) {

    private companion object {
        private const val MAX_RESULTS = 5000
        private const val MAX_FILE_BYTES = 5L * 1024 * 1024 // 5MB，避免误扫超大文件卡顿
        private val SKIP_DIR_NAMES = setOf(".git", ".gradle", "build", "out", ".idea", "dist")
        private val BINARY_EXTS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "ico",
            "so", "dll", "dylib",
            "jar", "apk", "dex", "class",
            "arsc", "ttf", "otf", "mp3", "mp4", "wav",
            "keystore", "jks",
        )
    }

    private val queryField = JTextField()
    private val matchCase = JCheckBox("区分大小写")
    private val regex = JCheckBox("正则")
    private val wholeWord = JCheckBox("全词")

    private val searchButton = JButton("搜索")
    private val stopButton = JButton("停止").apply { isEnabled = false }

    private val resultModel = DefaultListModel<SearchMatch>()
    private val resultList = JList(resultModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = ResultRenderer { workspaceRoot()?.toPath() }
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
        background = Color.WHITE
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        add(buildTopPanel(), BorderLayout.NORTH)
        add(JScrollPane(resultList).apply {
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, ThemeManager.separator)
            background = Color.WHITE
            viewport.background = Color.WHITE
        }, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        installListeners()
        updateStatusIdle()

        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent?) {
                focusQuery()
            }
        })
    }

    fun focusQuery() {
        SwingUtilities.invokeLater {
            queryField.requestFocusInWindow()
            queryField.selectAll()
        }
    }

    private fun buildTopPanel(): JPanel {
        val row1 = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            add(JLabel("搜索：").apply { foreground = Color(0x44, 0x44, 0x44) }, BorderLayout.WEST)
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

        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6)).apply {
            isOpaque = false
            add(matchCase)
            add(wholeWord)
            add(regex)
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
                    KeyEvent.VK_ESCAPE -> {
                        queryField.text = ""
                        updateStatusIdle()
                    }
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
                if (e.keyCode == KeyEvent.VK_ENTER) navigateToSelected()
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

        stopSearch()
        resultModel.clear()

        val token = ++searchToken
        val options = SearchOptions(
            query = query,
            matchCase = matchCase.isSelected,
            regex = regex.isSelected,
            wholeWord = wholeWord.isSelected,
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
        // 通过 token 失效避免旧任务在 done()/process() 中覆盖 UI 状态
        searchToken++
        worker?.cancel(true)
        worker = null
        stopButton.isEnabled = false
        searchButton.isEnabled = true
    }

    private fun navigateToSelected() {
        val match = resultList.selectedValue ?: return
        mainWindow.editor.openFileAndSelect(match.file, match.line, match.column, match.length)
    }

    private fun updateStatusIdle() {
        val root = workspaceRoot()
        statusLabel.text = if (root == null) "未打开工作区：只能使用 Ctrl+F 进行文件内查找" else "在工作区内搜索文件内容"
    }

    private fun workspaceRoot(): File? = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()

    private data class SearchOptions(
        val query: String,
        val matchCase: Boolean,
        val regex: Boolean,
        val wholeWord: Boolean,
    )

    private class SearchWorker(
        private val root: File,
        private val options: SearchOptions,
        private val onProgress: (filesScanned: Int, matches: Int) -> Unit,
        private val onMatch: (SearchMatch) -> Unit,
        private val onDone: (String) -> Unit,
    ) : SwingWorker<String, SearchMatch>() {

        private var filesScanned: Int = 0
        private var matches: Int = 0
        private var hitLimit: Boolean = false
        private val rootPath: Path = root.toPath()

        override fun doInBackground(): String {
            val startedAt = System.currentTimeMillis()
            val regexObj = if (options.regex) {
                try {
                    val opts = if (options.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    Regex(options.query, opts)
                } catch (_: Exception) {
                    return "正则表达式不合法，请检查后重试"
                }
            } else null

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

                    searchFile(file, regexObj)
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

        private fun searchFile(file: File, regexObj: Regex?) {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            InputStreamReader(FileInputStream(file), decoder).use { reader ->
                reader.buffered().readLinesWithLineNumber { lineNo, line ->
                    if (isCancelled) return@readLinesWithLineNumber false
                    val results = if (regexObj != null) {
                        regexObj.findAll(line).map { it.range.first to it.value.length }.toList()
                    } else {
                        findAllIndexes(line)
                    }

                    for ((col, len) in results) {
                        if (matches >= MAX_RESULTS) return@readLinesWithLineNumber false
                        publish(
                            SearchMatch(
                                file = file,
                                line = lineNo,
                                column = col,
                                length = len,
                                preview = line.take(200),
                            )
                        )
                        matches++
                    }
                    true
                }
            }
        }

        private fun findAllIndexes(line: String): List<Pair<Int, Int>> {
            val query = options.query
            if (query.isEmpty()) return emptyList()
            val list = ArrayList<Pair<Int, Int>>(1)
            var from = 0
            while (from <= line.length && list.size < 50) { // 单行最多收集 50 个，避免异常输入导致爆炸
                val idx = line.indexOf(query, startIndex = from, ignoreCase = !options.matchCase)
                if (idx < 0) break
                if (!options.wholeWord || isWholeWord(line, idx, query.length)) {
                    list.add(idx to query.length)
                }
                from = idx + maxOf(1, query.length)
            }
            return list
        }

        private fun isWholeWord(line: String, start: Int, length: Int): Boolean {
            fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'
            val leftOk = start <= 0 || !isWordChar(line[start - 1])
            val rightPos = start + length
            val rightOk = rightPos >= line.length || !isWordChar(line[rightPos])
            return leftOk && rightOk
        }

        private fun shouldSkip(path: Path): Boolean {
            val rel = try { rootPath.relativize(path).toString().replace('\\', '/') } catch (_: Exception) { path.toString() }
            val parts = rel.split('/')
            if (parts.any { it in SKIP_DIR_NAMES }) return true
            val name = path.fileName?.toString()?.lowercase().orEmpty()
            val ext = name.substringAfterLast('.', "")
            if (ext in BINARY_EXTS) return true
            return false
        }

        private fun isLikelyBinary(file: File): Boolean {
            return runCatching {
                FileInputStream(file).use { input ->
                    val buf = ByteArray(4096)
                    val n = input.read(buf)
                    if (n <= 0) return@runCatching false
                    for (i in 0 until n) {
                        if (buf[i].toInt() == 0) return@runCatching true
                    }
                    false
                }
            }.getOrDefault(true)
        }

        private inline fun java.io.BufferedReader.readLinesWithLineNumber(block: (lineNo: Int, line: String) -> Boolean) {
            var lineNo = 0
            while (true) {
                val line = readLine() ?: break
                lineNo++
                if (!block(lineNo, line)) break
            }
        }
    }

    private class ResultRenderer(
        private val rootProvider: () -> Path?,
    ) : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val match = value as? SearchMatch
            if (match == null) {
                c.text = ""
                return c
            }
            val root = rootProvider()
            val displayPath = root?.let {
                runCatching { it.relativize(match.file.toPath()).toString() }.getOrDefault(match.file.path)
            } ?: match.file.path
            c.text = "$displayPath:${match.line}:${match.column + 1}  ${match.preview.trim()}"
            c.font = c.font.deriveFont(Font.PLAIN, 12f)
            c.border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
            c.preferredSize = Dimension(0, c.preferredSize.height)
            return c
        }
    }
}
