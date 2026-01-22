package editorx.gui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import editorx.core.gui.GuiContext
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.PluginManager
import editorx.core.util.Store
import editorx.gui.MainWindow
import editorx.gui.RestartHelper
import editorx.gui.compose.ComposeHostPanel
import editorx.gui.compose.toComposeColor
import editorx.gui.compose.toMaterialColors
import editorx.gui.settings.compose.AppearanceSettingsPanel
import editorx.gui.settings.compose.CacheSettingsPanel
import editorx.gui.settings.compose.KeymapSettingsPanel
import editorx.gui.settings.compose.PluginsSettingsPanel
import editorx.gui.settings.compose.SettingsAlertRequest
import editorx.gui.settings.compose.SettingsNav
import editorx.gui.theme.Theme
import editorx.gui.theme.ThemeManager
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Locale
import javax.swing.JDialog
import javax.swing.SwingUtilities

/**
 * 设置弹窗（Compose 版本）。
 *
 * 说明：
 * - 弹窗框架由 Swing 承载（JDialog + ComposeHostPanel），内容全部由 Compose Desktop 渲染
 * - 语言与主题切换需要重启：仅写入设置，提示重启；不在运行时强制刷新
 */
class SettingsDialog(
    owner: MainWindow,
    private val environment: GuiContext,
    private val pluginManager: PluginManager,
    private val defaultSection: Section = Section.APPEARANCE,
) : JDialog(owner, I18n.translate(I18nKeys.Settings.TITLE), true) {

    enum class Section { APPEARANCE, KEYMAP, PLUGINS, CACHE }

    companion object {
        @Volatile
        private var currentInstance: SettingsDialog? = null

        /**
         * 显示设置对话框，如果已存在则将其带到前台
         */
        fun showOrBringToFront(
            owner: MainWindow,
            environment: GuiContext,
            pluginManager: PluginManager,
            defaultSection: Section = Section.APPEARANCE
        ) {
            val existing = currentInstance
            if (existing != null && existing.isVisible) {
                existing.toFront()
                existing.requestFocus()
                return
            }

            val dialog = SettingsDialog(owner, environment, pluginManager, defaultSection)
            currentInstance = dialog

            dialog.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    currentInstance = null
                }
            })

            dialog.isVisible = true
        }
    }

    // 兼容：供旧 Swing SettingsPanel 调用（当前弹窗已不再使用 Swing 面板，但保留方法避免编译断裂）
    private val changeToken = mutableIntStateOf(0)
    fun onPanelChangesUpdated() {
        SwingUtilities.invokeLater { changeToken.intValue++ }
    }

    // i18n 变化触发 Compose 重组
    private val i18nToken = mutableIntStateOf(0)
    private val i18nListener = {
        SwingUtilities.invokeLater {
            title = I18n.translate(I18nKeys.Settings.TITLE)
            i18nToken.intValue++
        }
    }

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        isResizable = true
        size = Dimension(940, 640)
        setLocationRelativeTo(owner)

        contentPane = ComposeHostPanel().apply {
            setContent { SettingsDialogScreen() }
        }

        I18n.addListener(i18nListener)
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                I18n.removeListener(i18nListener)
            }
        })
    }

    @Composable
    private fun SettingsDialogScreen() {
        // 监听主题变更
        var currentTheme by remember { mutableStateOf(ThemeManager.currentTheme) }
        DisposableEffect(Unit) {
            val listener: () -> Unit = { SwingUtilities.invokeLater { currentTheme = ThemeManager.currentTheme } }
            ThemeManager.addThemeChangeListener(listener)
            onDispose { ThemeManager.removeThemeChangeListener(listener) }
        }

        val colors = remember(currentTheme) { currentTheme.toMaterialColors() }
        MaterialTheme(colors = colors) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.surface) {
                SettingsDialogContent(theme = currentTheme)
            }
        }
    }

    @Composable
    private fun SettingsDialogContent(theme: Theme) {
        // 读取 token 以触发重组
        val i18nTick = i18nToken.intValue
        val changeTick = changeToken.intValue
        @Suppress("UNUSED_VARIABLE")
        val recompositionTick = i18nTick + changeTick

        val settings = environment.getSettings()
        val coroutineScope = rememberCoroutineScope()

        var selectedSection by remember { mutableStateOf(defaultSection) }
        var alert by remember { mutableStateOf<SettingsAlertRequest?>(null) }

        // 外观设置状态
        val savedLocale = remember { readLocaleFromSettings(settings) }
        val savedTheme = remember { readThemeFromSettings(settings) }
        var pendingLocale by remember { mutableStateOf<Locale?>(null) }
        var pendingTheme by remember { mutableStateOf<Theme?>(null) }
        val currentLocaleToShow = pendingLocale ?: savedLocale
        val hasPendingLocale = pendingLocale != null && pendingLocale != savedLocale
        val currentThemeToShow = pendingTheme ?: savedTheme
        val hasPendingTheme = pendingTheme != null && pendingTheme != savedTheme

        fun selectTheme(target: Theme) {
            pendingTheme = target
        }

        fun resetToDefault() {
            pendingTheme = Theme.Light
            val defaultLocale = Locale.SIMPLIFIED_CHINESE
            val available = I18n.getAvailableLocales()
            pendingLocale = if (available.contains(defaultLocale)) defaultLocale else available.firstOrNull()
        }

        fun applyAndClose() {
            val localeToSave = pendingLocale
            val currentLocaleTag = settings.get(SettingsStoreKeys.LOCALE, null)?.trim().orEmpty()
            val currentSavedLocale = currentLocaleTag.takeIf { it.isNotEmpty() }?.let {
                runCatching { Locale.forLanguageTag(it) }.getOrNull()
            }

            val localeChanged = localeToSave != null && localeToSave != currentSavedLocale
            if (localeToSave != null && localeChanged) {
                settings.put(SettingsStoreKeys.LOCALE, localeToSave.toLanguageTag())
                settings.sync()
            }

            val themeToSave = pendingTheme
            val themeChanged = themeToSave != null && themeToSave != savedTheme
            if (themeToSave != null && themeChanged) {
                settings.put(SettingsStoreKeys.THEME, ThemeManager.getThemeName(themeToSave))
                settings.sync()
            }

            val needRestart = localeChanged || themeChanged
            if (!needRestart) {
                dispose()
                return
            }

            alert = SettingsAlertRequest(
                title = I18n.translate(I18nKeys.Dialog.RESTART_REQUIRED),
                message = I18n.translate(I18nKeys.Dialog.RESTART_REQUIRED_MESSAGE),
                confirmText = I18n.translate(I18nKeys.Dialog.RESTART),
                dismissText = I18n.translate(I18nKeys.Dialog.LATER),
                onConfirm = {
                    dispose()
                    RestartHelper.restart()
                },
                onDismiss = { dispose() }
            )
        }

        val outline = theme.outline.toComposeColor()
        val surface = theme.surface.toComposeColor()
        val surfaceVariant = theme.surfaceVariant.toComposeColor()
        val primary = theme.primary.toComposeColor()
        val onPrimary = theme.onPrimary.toComposeColor()
        val onSurface = theme.onSurface.toComposeColor()
        val textMuted = theme.onSurfaceVariant.toComposeColor()
        
        // macOS Finder 风格的颜色，根据主题动态变化
        val isDark = theme is Theme.Dark
        val sidebarBackground = if (isDark) {
            theme.sidebarBackground.toComposeColor()
        } else {
            Color(0xFFfafafa)  // 浅色主题使用 Finder 风格的浅灰色
        }
        val contentBackground = theme.surface.toComposeColor()  // 使用主题的 surface 颜色
        val dividerColor = if (isDark) {
            outline.copy(alpha = 0.3f)  // 深色主题使用更明显的分割线
        } else {
            Color(0xFFE0E0E0)  // 浅色主题使用 Finder 风格的分割线
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surface)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key == Key.Escape) {
                        dispose()
                        true
                    } else {
                        false
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // 可调整宽度的导航栏
                    val minNavWidth = 180.dp
                    val maxNavWidth = 400.dp
                    var navWidth by remember { mutableStateOf(minNavWidth) } // 默认宽度为最小宽度
                    
                    Box(
                        modifier = Modifier
                            .width(navWidth)
                            .fillMaxHeight()
                            .background(sidebarBackground)
                    ) {
                        SettingsNav(
                            selectedSection = selectedSection,
                            hasAppearanceChanges = hasPendingLocale || hasPendingTheme,
                            outline = outline,
                            surface = surface,
                            primary = primary,
                            onPrimary = onPrimary,
                            onSurface = onSurface,
                            onSurfaceMuted = textMuted,
                            onSelect = { selectedSection = it }
                        )
                    }
                    
                    // 可拖动的分割线（macOS Finder 风格）
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(dividerColor)
                            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))) // 鼠标悬停时显示可拖动指针
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    val delta = dragAmount.x.toDp()
                                    val newWidth = (navWidth + delta).coerceIn(minNavWidth, maxNavWidth)
                                    navWidth = newWidth
                                    change.consume()
                                }
                            }
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(contentBackground)
                            .padding(horizontal = 32.dp, vertical = 32.dp)
                    ) {
                        when (selectedSection) {
                            Section.APPEARANCE -> AppearanceSettingsPanel(
                                theme = theme,
                                i18nVersion = i18nTick,
                                localeToShow = currentLocaleToShow,
                                themeToShow = currentThemeToShow,
                                hasPendingChanges = hasPendingLocale || hasPendingTheme,
                                onSelectLocale = { pendingLocale = it },
                                onRevertChanges = {
                                    pendingLocale = null
                                    pendingTheme = null
                                },
                                onSelectTheme = { selectTheme(it) }
                            )

                            Section.KEYMAP -> KeymapSettingsPanel(theme = theme, i18nVersion = i18nTick)
                            Section.PLUGINS -> PluginsSettingsPanel(
                                theme = theme,
                                settings = settings,
                                pluginManager = pluginManager,
                                parentDialog = this@SettingsDialog,
                                showAlert = { alert = it },
                                coroutineScope = coroutineScope
                            )

                            Section.CACHE -> CacheSettingsPanel(
                                theme = theme,
                                environment = environment,
                                showAlert = { alert = it },
                                coroutineScope = coroutineScope
                            )
                        }
                    }
                }

                // 底部操作栏上方的分割线
                Divider(color = dividerColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(contentBackground)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { resetToDefault() }) {
                        Text(
                            I18n.translate(I18nKeys.Action.RESET),
                            color = primary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { dispose() },
                            border = BorderStroke(0.5.dp, outline.copy(alpha = 0.3f))
                        ) {
                            Text(I18n.translate(I18nKeys.Action.CANCEL))
                        }
                        Button(onClick = { applyAndClose() }) {
                            Text(I18n.translate(I18nKeys.Action.CONFIRM))
                        }
                    }
                }
            }

            alert?.let { req ->
                AlertDialog(
                    onDismissRequest = { alert = null },
                    title = { Text(req.title) },
                    text = { Text(req.message) },
                    confirmButton = {
                        TextButton(onClick = { alert = null; req.onConfirm?.invoke() }) {
                            Text(req.confirmText)
                        }
                    },
                    dismissButton = req.dismissText?.let { dismissText ->
                        {
                            TextButton(onClick = { alert = null; req.onDismiss?.invoke() }) {
                                Text(dismissText)
                            }
                        }
                    }
                )
            }
        }
    }

    private fun readLocaleFromSettings(settings: Store): Locale {
        val savedLocaleTag = settings.get(SettingsStoreKeys.LOCALE, null)?.trim().orEmpty()
        return savedLocaleTag.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
            ?: I18n.locale()
    }

    private fun readThemeFromSettings(settings: Store): Theme {
        val savedThemeName = settings.get(SettingsStoreKeys.THEME, null)?.trim().orEmpty()
        return if (savedThemeName.isNotEmpty()) {
            ThemeManager.loadTheme(savedThemeName)
        } else {
            ThemeManager.currentTheme
        }
    }
}
