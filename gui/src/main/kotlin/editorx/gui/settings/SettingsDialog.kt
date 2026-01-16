package editorx.gui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * - 语言切换需要重启：仅写入设置，提示重启；不在运行时强制刷新全局语言
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
        var pendingLocale by remember { mutableStateOf<Locale?>(null) }
        val currentLocaleToShow = pendingLocale ?: savedLocale
        val hasPendingLocale = pendingLocale != null && pendingLocale != savedLocale

        fun applyTheme(target: Theme) {
            if (ThemeManager.currentTheme == target) return
            ThemeManager.currentTheme = target
            settings.put(SettingsStoreKeys.THEME, ThemeManager.getThemeName(target))
            settings.sync()
        }

        fun resetToDefault() {
            applyTheme(Theme.Light)
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

            val needRestart = localeToSave != null && localeToSave != currentSavedLocale
            if (localeToSave != null && needRestart) {
                settings.put(SettingsStoreKeys.LOCALE, localeToSave.toLanguageTag())
                settings.sync()
            }

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
                    SettingsNav(
                        selectedSection = selectedSection,
                        hasAppearanceChanges = hasPendingLocale,
                        outline = outline,
                        surface = surface,
                        primary = primary,
                        onPrimary = onPrimary,
                        onSurface = onSurface,
                        onSurfaceMuted = textMuted,
                        onSelect = { selectedSection = it }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(surfaceVariant)
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(surface, RoundedCornerShape(10.dp))
                                .border(1.dp, outline, RoundedCornerShape(10.dp))
                                .padding(16.dp)
                        ) {
                            when (selectedSection) {
                                Section.APPEARANCE -> AppearanceSettingsPanel(
                                    theme = theme,
                                    i18nVersion = i18nTick,
                                    localeToShow = currentLocaleToShow,
                                    hasPendingLocale = hasPendingLocale,
                                    onSelectLocale = { pendingLocale = it },
                                    onRevertLocale = { pendingLocale = null },
                                    onSelectTheme = { applyTheme(it) }
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
                }

                Divider(color = outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { resetToDefault() }) {
                        Text(I18n.translate(I18nKeys.Action.RESET))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { dispose() }) {
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
}
