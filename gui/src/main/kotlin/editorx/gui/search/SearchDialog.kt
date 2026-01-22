package editorx.gui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Colors
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import editorx.gui.MainWindow
import editorx.gui.compose.ComposeHostPanel
import editorx.gui.theme.Theme
import editorx.gui.theme.ThemeManager
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Window
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import javax.swing.JDialog
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import kotlinx.coroutines.launch

/**
 * 全局搜索弹窗（Compose 版本）。
 *
 * - 仍以 Swing 的 JDialog 承载（不影响现有主窗口架构）
 * - UI 使用 Compose Desktop 渲染
 * - 搜索算法沿用原实现（Files.walk + SwingWorker），确保行为一致
 */
class SearchDialog(
    owner: Window,
    private val mainWindow: MainWindow
) : JDialog(owner, "全局搜索", Dialog.ModalityType.APPLICATION_MODAL) {

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

    private var worker: SearchWorker? = null
    private var searchToken: Int = 0

    private val focusRequestToken = mutableIntStateOf(0)

    private var queryText by mutableStateOf("")
    private var currentQuery by mutableStateOf("")
    private var errorText by mutableStateOf<String?>(null)
    private var searchClass by mutableStateOf(true)
    private var searchMethod by mutableStateOf(true)
    private var searchField by mutableStateOf(true)
    private var searchCode by mutableStateOf(true)
    private var searchResource by mutableStateOf(true)
    private var searchFile by mutableStateOf(true)

    private var isSearching by mutableStateOf(false)
    private var statusText by mutableStateOf("输入搜索内容并按 Enter 或点击搜索按钮")

    private val results = mutableStateListOf<SearchMatch>()
    private val selectedIndex = mutableIntStateOf(-1)

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isResizable = true
        size = Dimension(800, 600)
        setLocationRelativeTo(owner)

        contentPane = ComposeHostPanel().apply {
            setContent { SearchDialogScreen() }
        }
    }

    override fun dispose() {
        stopSearch()
        super.dispose()
    }

    fun showDialog() {
        isVisible = true
        // 触发 Compose 侧重新请求输入框焦点
        focusRequestToken.intValue++
    }

    private fun startSearch() {
        val query = queryText.trim()
        if (query.isBlank()) {
            errorText = "请输入要搜索的内容"
            return
        }

        val root = workspaceRoot()
        if (root == null) {
            errorText = "请先打开文件夹（工作区），再进行全局搜索"
            return
        }

        if (!searchClass && !searchMethod && !searchField && !searchCode && !searchResource && !searchFile) {
            errorText = "请至少选择一个搜索类型"
            return
        }

        errorText = null
        stopSearch()
        results.clear()
        selectedIndex.intValue = -1

        SearchHistory.record(query)
        currentQuery = query

        val token = ++searchToken
        val options = SearchOptions(
            query = query,
            searchClass = searchClass,
            searchMethod = searchMethod,
            searchField = searchField,
            searchCode = searchCode,
            searchResource = searchResource,
            searchFile = searchFile,
        )

        val newWorker = SearchWorker(
            root = root,
            options = options,
            onProgress = onProgress@{ filesScanned, matches ->
                if (token != searchToken) return@onProgress
                statusText = "已扫描 $filesScanned 个文件，找到 $matches 条结果"
            },
            onMatch = onMatch@{ match ->
                if (token != searchToken) return@onMatch
                results.add(match)
                if (selectedIndex.intValue < 0) {
                    selectedIndex.intValue = 0
                }
            },
            onDone = onDone@{ summary ->
                if (token != searchToken) return@onDone
                statusText = summary
                isSearching = false
                worker = null
            }
        )

        worker = newWorker
        isSearching = true
        statusText = "搜索中…"
        newWorker.execute()
    }

    private fun stopSearch() {
        searchToken++
        worker?.cancel(true)
        worker = null
        isSearching = false
    }

    private fun navigateTo(match: SearchMatch) {
        mainWindow.editor.openFileAndSelect(match.file, match.line, match.column, match.length)
        dispose()
    }

    private fun workspaceRoot(): File? = mainWindow.guiContext.getWorkspace().getWorkspaceRoot()

    private fun relPath(file: File): String {
        val root = workspaceRoot()?.toPath() ?: return file.name
        return runCatching { root.relativize(file.toPath()).toString() }.getOrDefault(file.name)
    }

    @Composable
    private fun SearchDialogScreen() {
        var currentTheme by remember { mutableStateOf(ThemeManager.currentTheme) }

        DisposableEffect(Unit) {
            val listener: () -> Unit = {
                SwingUtilities.invokeLater {
                    currentTheme = ThemeManager.currentTheme
                }
            }
            ThemeManager.addThemeChangeListener(listener)
            onDispose { ThemeManager.removeThemeChangeListener(listener) }
        }

        val colors = remember(currentTheme) { currentTheme.toMaterialColors() }
        MaterialTheme(colors = colors) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.surface) {
                SearchDialogContent(currentTheme)
            }
        }
    }

    @Composable
    private fun SearchDialogContent(theme: Theme) {
        val palette = remember(theme) { figmaSearchPalette(theme) }
        val focusRequester = remember { FocusRequester() }
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        val hasResults by remember { derivedStateOf { results.isNotEmpty() } }

        var isQueryFocused by remember { mutableStateOf(false) }

        fun navigateSelected() {
            val idx = selectedIndex.intValue
            if (idx in results.indices) {
                navigateTo(results[idx])
            }
        }

        fun selectIndex(idx: Int) {
            if (results.isEmpty()) {
                selectedIndex.intValue = -1
                return
            }
            val newIndex = idx.coerceIn(0, results.lastIndex)
            selectedIndex.intValue = newIndex
            coroutineScope.launch {
                runCatching { listState.animateScrollToItem(newIndex) }
            }
        }

        LaunchedEffect(focusRequestToken.intValue) {
            runCatching { focusRequester.requestFocus() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.cardBackground)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            dispose()
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            if (isQueryFocused) {
                                startSearch()
                            } else {
                                navigateSelected()
                            }
                            true
                        }

                        Key.DirectionDown -> {
                            if (hasResults) {
                                if (isQueryFocused) {
                                    focusManager.clearFocus(force = true)
                                    isQueryFocused = false
                                }
                                val next = (selectedIndex.intValue + 1).coerceAtMost(results.lastIndex)
                                selectIndex(next)
                                true
                            } else false
                        }

                        Key.DirectionUp -> {
                            if (hasResults) {
                                if (isQueryFocused) {
                                    focusManager.clearFocus(force = true)
                                    isQueryFocused = false
                                }
                                val prev = (selectedIndex.intValue - 1).coerceAtLeast(0)
                                selectIndex(prev)
                                true
                            } else false
                        }

                        else -> false
                    }
                }
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.cardBackground)
            ) {
                // 搜索输入框
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchInput(
                        modifier = Modifier.weight(1f),
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = "搜索类、方法、字段...",
                        focusRequester = focusRequester,
                        palette = palette,
                        theme = theme,
                        onFocusChanged = { isFocused -> isQueryFocused = isFocused }
                    )
                    SearchButton(
                        enabled = !isSearching && queryText.trim().isNotEmpty(),
                        searching = isSearching,
                        palette = palette,
                        theme = theme,
                        onClick = { startSearch() }
                    )
                }

                // 搜索范围复选框
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "搜索范围",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    ScopeChip(
                        label = "类",
                        checked = searchClass,
                        palette = palette,
                        theme = theme,
                        onCheckedChange = { searchClass = it }
                    )
                    ScopeChip(
                        label = "方法",
                        checked = searchMethod,
                        palette = palette,
                        theme = theme,
                        onCheckedChange = { searchMethod = it }
                    )
                    ScopeChip(
                        label = "字段",
                        checked = searchField,
                        palette = palette,
                        theme = theme,
                        onCheckedChange = { searchField = it }
                    )
                    ScopeChip(
                        label = "代码",
                        checked = searchCode,
                        palette = palette,
                        theme = theme,
                        onCheckedChange = { searchCode = it }
                    )
                    ScopeChip(
                        label = "资源",
                        checked = searchResource,
                        palette = palette,
                        theme = theme,
                        onCheckedChange = { searchResource = it }
                    )
                    ScopeChip(
                        label = "文件",
                        checked = searchFile,
                        palette = palette,
                        theme = theme,
                        onCheckedChange = { searchFile = it }
                    )
                }

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText.orEmpty(),
                        color = palette.errorText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }

            // Results
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isSearching -> SearchingState(palette = palette, theme = theme, statusText = statusText)
                    currentQuery.isBlank() -> EmptyState(
                        palette = palette,
                        title = "输入关键词并点击搜索",
                        subtitle = "或按 Enter 键快速搜索",
                        showIcon = false
                    )

                    results.isNotEmpty() -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = results,
                                key = { _, match -> "${match.file.absolutePath}:${match.line}:${match.column}:${match.type}" }
                            ) { index, match ->
                                val selected = index == selectedIndex.intValue
                                SearchResultRow(
                                    match = match,
                                    relPath = relPath(match.file),
                                    selected = selected,
                                    palette = palette,
                                    theme = theme,
                                    onDoubleClick = {
                                        selectedIndex.intValue = index
                                        navigateTo(match)
                                    }
                                )
                                if (index < results.lastIndex) {
                                    Divider(color = palette.dividerColor)
                                }
                            }
                        }
                    }

                    else -> EmptyState(
                        palette = palette,
                        title = "未找到匹配的结果",
                        subtitle = "尝试使用其他关键词"
                    )
                }
            }

            // Footer
            FooterBar(
                resultCount = results.size,
                palette = palette,
            )
        }
    }

    @Composable
    private fun SearchInput(
        modifier: Modifier = Modifier,
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        focusRequester: FocusRequester,
        palette: FigmaSearchPalette,
        theme: Theme,
        onFocusChanged: (Boolean) -> Unit,
    ) {
        var focused by remember { mutableStateOf(false) }
        val primaryColor = theme.primary.toComposeColor()

        Box(
            modifier = modifier
                .height(40.dp)
                .background(
                    color = if (focused) palette.inputBackgroundFocused else palette.inputBackground,
                    shape = palette.inputShape
                )
                .border(
                    width = if (focused) 1.5.dp else 1.dp,
                    color = if (focused) primaryColor else palette.inputBorder,
                    shape = palette.inputShape
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍", color = palette.placeholderText, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = SolidColor(primaryColor),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChanged(it.isFocused)
                        },
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (value.isBlank()) {
                                Text(
                                    text = placeholder,
                                    color = palette.placeholderText,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun SearchButton(
        enabled: Boolean,
        searching: Boolean,
        palette: FigmaSearchPalette,
        theme: Theme,
        onClick: () -> Unit,
    ) {
        val primaryColor = theme.primary.toComposeColor()
        val onPrimaryColor = theme.onPrimary.toComposeColor()
        
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = primaryColor,
                contentColor = onPrimaryColor,
                disabledBackgroundColor = palette.primaryButtonDisabledBackground,
                disabledContentColor = palette.textMuted,
            ),
            shape = palette.inputShape,
            modifier = Modifier.height(40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = onPrimaryColor,
                        strokeWidth = 2.dp
                    )
                    Text("搜索中", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                } else {
                    Text("搜索", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterialApi::class)
    private fun ScopeChip(
        label: String,
        checked: Boolean,
        palette: FigmaSearchPalette,
        theme: Theme,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val hovered by interactionSource.collectIsHoveredAsState()
        val primaryColor = theme.primary.toComposeColor()

        Row(
            modifier = Modifier
                .hoverable(interactionSource)
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.size(14.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = primaryColor,
                        uncheckedColor = palette.checkboxUnchecked,
                        checkmarkColor = Color.White,
                    )
                )
            }
            Text(
                label,
                color = if (checked) palette.textPrimary else palette.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal
            )
        }
    }

    @Composable
    private fun SearchingState(palette: FigmaSearchPalette, theme: Theme, statusText: String) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = theme.primary.toComposeColor(),
                strokeWidth = 4.dp
            )
            Spacer(Modifier.height(12.dp))
            Text("正在搜索...", color = palette.textSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (statusText.isNotBlank()) statusText else "处理中，请稍候",
                color = palette.textMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }

    @Composable
    private fun EmptyState(
        palette: FigmaSearchPalette,
        title: String,
        subtitle: String,
        showIcon: Boolean = true,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showIcon) {
                Text("🔍", color = palette.emptyIcon, fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
            }
            Text(title, color = palette.textSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = palette.textMuted, fontSize = 14.sp)
        }
    }

    @Composable
    private fun SearchResultRow(
        match: SearchMatch,
        relPath: String,
        selected: Boolean,
        palette: FigmaSearchPalette,
        theme: Theme,
        onDoubleClick: () -> Unit,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val hovered by interactionSource.collectIsHoveredAsState()
        val (badgeBg, badgeFg) = remember(match.type, palette.isDark) {
            figmaBadgePalette(match.type, palette.isDark)
        }
        val background = when {
            selected -> palette.selectedBackground
            hovered -> palette.hoverBackground
            else -> palette.cardBackground
        }
        val titleColor = if (hovered) palette.linkHover else palette.textPrimary
        val primaryColor = theme.primary.toComposeColor()
        val highlightBg = remember(primaryColor, palette.isDark) {
            primaryColor.copy(alpha = if (palette.isDark) 0.35f else 0.22f)
        }
        val highlightStyle = remember(highlightBg) { SpanStyle(background = highlightBg) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .hoverable(interactionSource)
                .clickable { onDoubleClick() }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(28.dp)
                    .background(badgeBg, palette.badgeShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = match.type.displayName.take(1),
                    color = badgeFg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = buildHighlightText(matchTitle(match, relPath), currentQuery, highlightStyle),
                        color = titleColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (match.type != SearchMatchType.UNKNOWN || match.line > 1) {
                        Text(
                            text = ":${match.line}",
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = relPath,
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 对于文件搜索（UNKNOWN类型且preview是文件名），不显示代码预览
                val isFileSearch = match.type == SearchMatchType.UNKNOWN && 
                    match.preview.trim() == match.file.name && 
                    match.line == 1 && 
                    match.column == 0
                
                if (!isFileSearch) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.codeBackground, palette.codeShape)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        val previewText = match.preview.trimEnd()
                        Text(
                            text = buildHighlightText(previewText, currentQuery, highlightStyle),
                            color = palette.codeText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    private fun matchTitle(match: SearchMatch, relPath: String): String {
        val preview = match.preview
        return when (match.type) {
            SearchMatchType.CLASS,
            SearchMatchType.METHOD,
            SearchMatchType.FIELD -> {
                if (match.column >= 0 && match.column + match.length <= preview.length) {
                    preview.substring(match.column, match.column + match.length).trim().ifBlank { relPath }
                } else {
                    relPath
                }
            }

            SearchMatchType.RESOURCE -> match.file.name
            SearchMatchType.CODE,
            SearchMatchType.UNKNOWN -> preview.trim().ifBlank { relPath }
        }
    }

    @Composable
    private fun FooterBar(
        resultCount: Int,
        palette: FigmaSearchPalette,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.footerBackground)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("找到", color = palette.footerText, fontSize = 12.sp)
                Text(
                    "$resultCount",
                    color = palette.footerNumber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text("项结果", color = palette.footerText, fontSize = 12.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (System.getProperty("os.name").lowercase().contains("mac")) "⌘K" else "Ctrl+K",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(palette.keycapBackground.copy(alpha = 0.6f), palette.keycapShape)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
                Text("打开搜索", color = palette.textMuted, fontSize = 12.sp)
            }
        }
    }

    private fun buildHighlightText(
        text: String,
        query: String,
        highlight: SpanStyle,
    ): AnnotatedString {
        if (query.isBlank() || text.isBlank()) return AnnotatedString(text)

        return buildAnnotatedString {
            var startIndex = 0
            while (startIndex < text.length) {
                val idx = text.indexOf(query, startIndex = startIndex, ignoreCase = true)
                if (idx < 0) {
                    append(text.substring(startIndex))
                    break
                }

                if (idx > startIndex) {
                    append(text.substring(startIndex, idx))
                }

                val end = (idx + query.length).coerceAtMost(text.length)
                withStyle(highlight) {
                    append(text.substring(idx, end))
                }
                startIndex = end
            }
        }
    }

    private data class SearchOptions(
        val query: String,
        val searchClass: Boolean,
        val searchMethod: Boolean,
        val searchField: Boolean,
        val searchCode: Boolean,
        val searchResource: Boolean,
        val searchFile: Boolean,
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
                    
                    filesScanned++
                    if (filesScanned % 50 == 0) {
                        publishProgress()
                    }
                    
                    // 文件搜索：根据文件名匹配（不检查文件大小和二进制）
                    if (options.searchFile) {
                        val fileName = file.name.lowercase()
                        if (fileName.contains(queryLower)) {
                            matches++
                            publishMatch(
                                SearchMatch(
                                    file = file,
                                    line = 1,
                                    column = 0,
                                    length = queryLower.length,
                                    preview = file.name,
                                    type = SearchMatchType.UNKNOWN,
                                )
                            )
                            if (matches >= MAX_RESULTS) {
                                hitLimit = true
                                break
                            }
                            // 如果只搜索文件名，跳过内容搜索
                            if (!options.searchClass && !options.searchMethod && !options.searchField && 
                                !options.searchCode && !options.searchResource) {
                                continue
                            }
                        }
                    }
                    
                    // 内容搜索需要检查文件大小和二进制
                    if (file.length() > MAX_FILE_BYTES) continue
                    if (isLikelyBinary(file)) continue

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

        private fun publishMatch(match: SearchMatch) {
            SwingUtilities.invokeLater { onMatch(match) }
        }

        private fun searchFile(file: File, queryLower: String) {
            val fileExt = file.extension.lowercase()

            val isJavaFile = fileExt in setOf("java", "kt", "kts")
            val isSmaliFile = fileExt == "smali"
            val isXmlFile = fileExt == "xml"
            val isResourceFile = fileExt in setOf("xml", "png", "jpg", "jpeg", "gif", "webp", "ico", "ttf", "otf")

            // 文件搜索已在 doInBackground 中处理，这里跳过
            if (options.searchFile) return
            
            var shouldSearch = false
            if (options.searchCode) shouldSearch = true
            if (options.searchClass && (isJavaFile || isSmaliFile)) shouldSearch = true
            if (options.searchMethod && (isJavaFile || isSmaliFile)) shouldSearch = true
            if (options.searchField && (isJavaFile || isSmaliFile)) shouldSearch = true
            if (options.searchResource && isResourceFile) shouldSearch = true
            if (!shouldSearch) return

            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)

            InputStreamReader(FileInputStream(file), decoder).use { reader ->
                reader.buffered().readLinesWithLineNumber { lineNo, line ->
                    if (isCancelled) return@readLinesWithLineNumber false

                    val lineLower = line.lowercase()
                    if (!lineLower.contains(queryLower)) return@readLinesWithLineNumber true

                    val matchType = detectMatchType(line, queryLower, isJavaFile, isSmaliFile, isXmlFile)
                    if (matchType == null) return@readLinesWithLineNumber true

                    val col = lineLower.indexOf(queryLower)
                    if (col >= 0 && matches < MAX_RESULTS) {
                        val maxPreview = 200
                        var start = (col - 60).coerceAtLeast(0)
                        var end = (start + maxPreview).coerceAtMost(line.length)
                        if (end - start < maxPreview) {
                            start = (end - maxPreview).coerceAtLeast(0)
                        }
                        val preview = line.substring(start, end)
                        val previewCol = col - start

                        matches++
                        publishMatch(
                            SearchMatch(
                                file = file,
                                line = lineNo,
                                column = previewCol,
                                length = queryLower.length,
                                preview = preview,
                                type = matchType.toSearchMatchType(),
                            )
                        )
                        publishProgress()
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
            isXmlFile: Boolean,
        ): String? {
            val lineLower = line.lowercase().trim()

            if (isJavaFile || isSmaliFile) {
                if (options.searchClass) {
                    if (isSmaliFile) {
                        val desc = extractSmaliClassDescriptor(lineLower)
                        if (desc != null) {
                            val normalizedQuery = normalizeSmaliQuery(queryLower)
                            val simpleName = desc.substringAfterLast('/')
                            if (normalizedQuery == desc || normalizedQuery == simpleName) {
                                return "class"
                            }
                        }
                    } else {
                        val escapedQuery = Pattern.quote(queryLower)
                        if (
                            Pattern.compile("\\b(class|interface|enum|object|record)\\s+$escapedQuery\\b").matcher(lineLower).find() ||
                            Pattern.compile("@interface\\s+$escapedQuery\\b").matcher(lineLower).find() ||
                            Pattern.compile("\\bannotation\\s+class\\s+$escapedQuery\\b").matcher(lineLower).find()
                        ) {
                            return "class"
                        }
                    }
                }
                if (options.searchMethod) {
                    val escapedQuery = Pattern.quote(queryLower)
                    if (
                        lineLower.contains("fun $queryLower") ||
                        lineLower.contains("function $queryLower") ||
                        Pattern.compile("\\b$escapedQuery\\s*\\(").matcher(lineLower).find()
                    ) {
                        return "method"
                    }
                }
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

            if (options.searchCode) {
                return "code"
            }

            return null
        }

        private fun extractSmaliClassDescriptor(lineLower: String): String? {
            if (!lineLower.startsWith(".class")) return null
            val match = Pattern.compile("l[^;]+;").matcher(lineLower)
            if (!match.find()) return null
            val raw = match.group()
            if (raw.length <= 2) return null
            return raw.substring(1, raw.length - 1)
        }

        private fun normalizeSmaliQuery(queryLower: String): String {
            var q = queryLower.trim()
            if (q.startsWith("l") && q.endsWith(";") && q.length > 2) {
                q = q.substring(1, q.length - 1)
            }
            return q.replace('.', '/')
        }

        private fun shouldSkip(path: Path): Boolean {
            val rel = try {
                rootPath.relativize(path).toString().replace('\\', '/')
            } catch (_: Exception) {
                path.toString()
            }
            val parts = rel.split('/')
            if (parts.any { it in SKIP_DIR_NAMES }) return true

            val name = path.fileName?.toString()?.lowercase().orEmpty()
            if (name.startsWith(".")) return true

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
    }
}

private data class FigmaSearchPalette(
    val isDark: Boolean,
    val outerBackground: Color,
    val cardBackground: Color,
    val borderColor: Color,
    val dividerColor: Color,
    val hoverBackground: Color,
    val selectedBackground: Color,
    val inputBackground: Color,
    val inputBackgroundFocused: Color,
    val inputBorder: Color,
    val focusColor: Color,
    val placeholderText: Color,
    val checkboxUnchecked: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textChip: Color,
    val linkHover: Color,
    val primaryButtonBackground: Color,
    val primaryButtonDisabledBackground: Color,
    val footerBackground: Color,
    val footerText: Color,
    val footerNumber: Color,
    val keycapBackground: Color,
    val keycapBorder: Color,
    val emptyIcon: Color,
    val codeBackground: Color,
    val codeText: Color,
    val errorText: Color,
    val cardShape: RoundedCornerShape,
    val inputShape: RoundedCornerShape,
    val chipShape: RoundedCornerShape,
    val badgeShape: RoundedCornerShape,
    val codeShape: RoundedCornerShape,
    val keycapShape: RoundedCornerShape,
)

private fun figmaSearchPalette(theme: Theme): FigmaSearchPalette {
    val isDark = theme is Theme.Dark
    return if (!isDark) {
        FigmaSearchPalette(
            isDark = false,
            outerBackground = Color(0xFFF9FAFB), // gray-50
            cardBackground = Color.White,
            borderColor = Color(0xFFE5E7EB), // gray-200
            dividerColor = Color(0xFFF3F4F6), // gray-100
            hoverBackground = Color(0xFFF9FAFB), // gray-50
            selectedBackground = Color(0xFFEFF6FF), // blue-50
            inputBackground = Color(0xFFF5F5F7),
            inputBackgroundFocused = Color.White,
            inputBorder = Color(0xFFD1D1D6),
            focusColor = Color(0xFF3B82F6), // blue-500
            placeholderText = Color(0xFF9CA3AF), // gray-400
            checkboxUnchecked = Color(0xFFD1D5DB), // gray-300
            textPrimary = Color(0xFF111827), // gray-900
            textSecondary = Color(0xFF6B7280), // gray-500
            textMuted = Color(0xFF9CA3AF), // gray-400
            textChip = Color(0xFF374151), // gray-700
            linkHover = Color(0xFF2563EB), // blue-600
            primaryButtonBackground = Color(0xFF3B82F6), // blue-500
            primaryButtonDisabledBackground = Color(0xFFD1D5DB), // gray-300
            footerBackground = Color(0xFFF5F5F7), // 更接近Apple的浅灰
            footerText = Color(0xFF4B5563), // gray-600
            footerNumber = Color(0xFF111827), // gray-900
            keycapBackground = Color.White,
            keycapBorder = Color(0xFFD1D5DB), // gray-300
            emptyIcon = Color(0xFFD1D5DB), // gray-300
            codeBackground = Color(0xFFF9FAFB), // gray-50
            codeText = Color(0xFF4B5563), // gray-600
            errorText = Color(0xFFD4183D),
            cardShape = RoundedCornerShape(16.dp),
            inputShape = RoundedCornerShape(8.dp),
            chipShape = RoundedCornerShape(6.dp),
            badgeShape = RoundedCornerShape(6.dp),
            codeShape = RoundedCornerShape(6.dp),
            keycapShape = RoundedCornerShape(4.dp),
        )
    } else {
        FigmaSearchPalette(
            isDark = true,
            outerBackground = Color(0xFF0B0F17),
            cardBackground = Color(0xFF111827),
            borderColor = Color(0xFF1F2937),
            dividerColor = Color(0xFF1F2937),
            hoverBackground = Color(0xFF0F172A),
            selectedBackground = Color(0xFF1E3A8A).copy(alpha = 0.35f),
            inputBackground = Color(0xFF0F172A),
            inputBackgroundFocused = Color(0xFF111827),
            inputBorder = Color(0xFF1F2937),
            focusColor = Color(0xFF3B82F6),
            placeholderText = Color(0xFF6B7280),
            checkboxUnchecked = Color(0xFF4B5563),
            textPrimary = Color(0xFFF9FAFB),
            textSecondary = Color(0xFF9CA3AF),
            textMuted = Color(0xFF6B7280),
            textChip = Color(0xFFE5E7EB),
            linkHover = Color(0xFF60A5FA),
            primaryButtonBackground = Color(0xFF3B82F6),
            primaryButtonDisabledBackground = Color(0xFF374151),
            footerBackground = Color(0xFF0F172A),
            footerText = Color(0xFF9CA3AF),
            footerNumber = Color(0xFFF9FAFB),
            keycapBackground = Color(0xFF111827),
            keycapBorder = Color(0xFF374151),
            emptyIcon = Color(0xFF374151),
            codeBackground = Color(0xFF0F172A),
            codeText = Color(0xFF9CA3AF),
            errorText = Color(0xFFFB7185),
            cardShape = RoundedCornerShape(16.dp),
            inputShape = RoundedCornerShape(8.dp),
            chipShape = RoundedCornerShape(6.dp),
            badgeShape = RoundedCornerShape(6.dp),
            codeShape = RoundedCornerShape(6.dp),
            keycapShape = RoundedCornerShape(4.dp),
        )
    }
}

private fun figmaBadgePalette(type: SearchMatchType, isDark: Boolean): Pair<Color, Color> {
    return if (!isDark) {
        when (type) {
            SearchMatchType.CLASS -> Color(0xFFEDE9FE) to Color(0xFF6D28D9) // purple-100 / purple-700
            SearchMatchType.METHOD -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8) // blue-100 / blue-700
            SearchMatchType.FIELD -> Color(0xFFD1FAE5) to Color(0xFF047857) // green-100 / green-700
            SearchMatchType.CODE -> Color(0xFFFFEDD5) to Color(0xFFC2410C) // orange-100 / orange-700
            SearchMatchType.RESOURCE -> Color(0xFFFCE7F3) to Color(0xFFBE185D) // pink-100 / pink-700
            SearchMatchType.UNKNOWN -> Color(0xFFE5E7EB) to Color(0xFF374151) // gray-200 / gray-700
        }
    } else {
        when (type) {
            SearchMatchType.CLASS -> Color(0xFF6D28D9).copy(alpha = 0.25f) to Color(0xFFC4B5FD)
            SearchMatchType.METHOD -> Color(0xFF1D4ED8).copy(alpha = 0.25f) to Color(0xFFBFDBFE)
            SearchMatchType.FIELD -> Color(0xFF047857).copy(alpha = 0.25f) to Color(0xFFA7F3D0)
            SearchMatchType.CODE -> Color(0xFFC2410C).copy(alpha = 0.25f) to Color(0xFFFED7AA)
            SearchMatchType.RESOURCE -> Color(0xFFBE185D).copy(alpha = 0.25f) to Color(0xFFFBCFE8)
            SearchMatchType.UNKNOWN -> Color(0xFF374151).copy(alpha = 0.35f) to Color(0xFFE5E7EB)
        }
    }
}

private fun java.awt.Color.toComposeColor(): Color = Color(red, green, blue, alpha)

private fun String.toSearchMatchType(): SearchMatchType {
    return when (this) {
        "class" -> SearchMatchType.CLASS
        "method" -> SearchMatchType.METHOD
        "field" -> SearchMatchType.FIELD
        "resource" -> SearchMatchType.RESOURCE
        "code" -> SearchMatchType.CODE
        else -> SearchMatchType.UNKNOWN
    }
}

private fun Theme.toMaterialColors(): Colors {
    val primary = primary.toComposeColor()
    val onPrimary = onPrimary.toComposeColor()
    val secondary = secondary.toComposeColor()
    val onSecondary = onSecondary.toComposeColor()
    val background = surface.toComposeColor()
    val surface = surface.toComposeColor()
    val onSurface = onSurface.toComposeColor()
    val error = error.toComposeColor()

    return when (this) {
        is Theme.Dark -> darkColors(
            primary = primary,
            primaryVariant = primaryContainer.toComposeColor(),
            secondary = secondary,
            background = background,
            surface = surface,
            error = error,
            onPrimary = onPrimary,
            onSecondary = onSecondary,
            onBackground = onSurface,
            onSurface = onSurface,
            onError = error,
        )

        is Theme.Light -> lightColors(
            primary = primary,
            primaryVariant = primaryContainer.toComposeColor(),
            secondary = secondary,
            background = background,
            surface = surface,
            error = error,
            onPrimary = onPrimary,
            onSecondary = onSecondary,
            onBackground = onSurface,
            onSurface = onSurface,
            onError = error,
        )
    }
}

// 扩展函数：按行读取文件并带行号
private fun java.io.BufferedReader.readLinesWithLineNumber(
    action: (Int, String) -> Boolean
) {
    var lineNo = 0
    while (true) {
        val line = readLine() ?: break
        lineNo++
        if (!action(lineNo, line)) break
    }
}
