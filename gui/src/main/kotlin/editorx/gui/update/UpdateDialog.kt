package editorx.gui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import editorx.gui.MainWindow
import editorx.gui.compose.ComposeHostPanel
import editorx.gui.compose.toComposeColor
import editorx.gui.compose.toMaterialColors
import editorx.gui.theme.Theme
import editorx.gui.theme.ThemeManager
import java.awt.Dimension
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

object UpdateDialog {
    fun confirmUpdate(mainWindow: MainWindow, info: UpdateManager.UpdateAvailable): Boolean {
        val notes = (info.releaseNotes ?: "").trim().ifEmpty { "暂无更新说明" }
        var confirmed = false

        val dialog = JDialog(mainWindow, "检查更新", true).apply {
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            isResizable = false
        }

        val host = ComposeHostPanel().apply {
            setContent {
                UpdateConfirmDialog(
                    info = info,
                    notes = notes,
                    onConfirm = {
                        confirmed = true
                        dialog.dispose()
                    },
                    onCancel = { dialog.dispose() },
                )
            }
        }

        dialog.contentPane = host
        dialog.size = Dimension(720, 560)
        dialog.setLocationRelativeTo(mainWindow)
        dialog.isVisible = true
        return confirmed
    }

    fun confirmRestart(mainWindow: MainWindow, message: String): Boolean {
        val options = arrayOf("稍后", "立即重启")
        val result = JOptionPane.showOptionDialog(
            mainWindow,
            message,
            "更新准备完成",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[1]
        )
        return result == 1
    }

    fun showDownloadingDialog(mainWindow: MainWindow, title: String): DownloadingDialog {
        return DownloadingDialog(mainWindow, title)
    }

    @Composable
    private fun UpdateConfirmDialog(
        info: UpdateManager.UpdateAvailable,
        notes: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
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
                UpdateConfirmContent(
                    theme = currentTheme,
                    info = info,
                    notes = notes,
                    onConfirm = onConfirm,
                    onCancel = onCancel,
                )
            }
        }
    }

    private data class UpdatePalette(
        val surface: Color,
        val outline: Color,
        val onSurface: Color,
        val onSurfaceMuted: Color,
        val accent: Color,
        val accentSoft: Color,
        val onAccent: Color,
        val headerGradient: List<Color>,
        val noteBg: Color,
        val noteBorder: Color,
        val badgeBg: Color,
        val badgeText: Color,
    )

    @Composable
    private fun UpdateConfirmContent(
        theme: Theme,
        info: UpdateManager.UpdateAvailable,
        notes: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        val palette = remember(theme) {
            val accent = theme.primary.toComposeColor()
            val accentSoft = theme.primaryContainer.toComposeColor()
            val isDark = theme is Theme.Dark
            val onAccent = if (isDark) theme.onPrimaryContainer.toComposeColor() else theme.onPrimary.toComposeColor()
            UpdatePalette(
                surface = theme.surface.toComposeColor(),
                outline = theme.outline.toComposeColor(),
                onSurface = theme.onSurface.toComposeColor(),
                onSurfaceMuted = theme.onSurfaceVariant.toComposeColor(),
                accent = accent,
                accentSoft = accentSoft,
                onAccent = onAccent,
                headerGradient = if (isDark) listOf(accentSoft, accent) else listOf(accent, accent.copy(alpha = 0.86f)),
                noteBg = theme.cardBackground.toComposeColor(),
                noteBorder = theme.outline.toComposeColor().copy(alpha = 0.5f),
                badgeBg = if (isDark) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.22f),
                badgeText = onAccent,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.surface)
        ) {
            HeaderSection(info = info, palette = palette)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 22.dp)
            ) {
                Text(
                    text = info.releaseName?.trim().takeIf { !it.isNullOrBlank() } ?: "发现新版本",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "更新完成后将自动重启以应用新版本。",
                    fontSize = 13.sp,
                    color = palette.onSurfaceMuted,
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "更新内容",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurface,
                )

                Spacer(modifier = Modifier.height(10.dp))

                val scroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.noteBg)
                        .border(1.dp, palette.noteBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                        .verticalScroll(scroll)
                ) {
                    Text(
                        text = notes,
                        fontSize = 13.sp,
                        color = palette.onSurface,
                        lineHeight = 19.sp,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = palette.outline.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(1.dp, palette.outline.copy(alpha = 0.7f))
                    ) {
                        Text("取消", color = palette.onSurface)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = palette.accent,
                            contentColor = theme.onPrimary.toComposeColor(),
                        )
                    ) {
                        Text("更新并重启")
                    }
                }
            }
        }
    }

    @Composable
    private fun HeaderSection(
        info: UpdateManager.UpdateAvailable,
        palette: UpdatePalette,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.linearGradient(palette.headerGradient))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↑",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onAccent,
                        )
                    }
                    Column {
                        Text(
                            text = "检查到新版本",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onAccent,
                        )
                        Text(
                            text = "更快、更稳、更轻量",
                            fontSize = 12.sp,
                            color = palette.onAccent.copy(alpha = 0.85f),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.badgeBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "NEW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.badgeText,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VersionChip(label = "当前版本", value = info.currentVersion, palette = palette)
                    VersionChip(
                        label = "最新版本",
                        value = info.latestVersion,
                        palette = palette,
                        highlight = true,
                    )
                }
            }
        }
    }

    @Composable
    private fun VersionChip(
        label: String,
        value: String,
        palette: UpdatePalette,
        highlight: Boolean = false,
    ) {
        val bg = if (highlight) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f)
        val border = Color.White.copy(alpha = if (highlight) 0.5f else 0.3f)
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = palette.onAccent.copy(alpha = 0.85f),
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.onAccent,
            )
        }
    }

    private data class DownloadPalette(
        val surface: Color,
        val onSurface: Color,
        val onSurfaceMuted: Color,
        val outline: Color,
        val accent: Color,
        val accentSoft: Color,
        val onAccent: Color,
        val headerGradient: List<Color>,
        val cardBg: Color,
        val cardBorder: Color,
    )

    @Composable
    private fun DownloadingDialogScreen(
        message: String,
        percent: Int?,
        onCancel: () -> Unit,
    ) {
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
                DownloadingDialogContent(
                    theme = currentTheme,
                    message = message,
                    percent = percent,
                    onCancel = onCancel,
                )
            }
        }
    }

    @Composable
    private fun DownloadingDialogContent(
        theme: Theme,
        message: String,
        percent: Int?,
        onCancel: () -> Unit,
    ) {
        val palette = remember(theme) {
            val accent = theme.primary.toComposeColor()
            val accentSoft = theme.primaryContainer.toComposeColor()
            val isDark = theme is Theme.Dark
            val onAccent = if (isDark) theme.onPrimaryContainer.toComposeColor() else theme.onPrimary.toComposeColor()
            DownloadPalette(
                surface = theme.surface.toComposeColor(),
                onSurface = theme.onSurface.toComposeColor(),
                onSurfaceMuted = theme.onSurfaceVariant.toComposeColor(),
                outline = theme.outline.toComposeColor(),
                accent = accent,
                accentSoft = accentSoft,
                onAccent = onAccent,
                headerGradient = if (isDark) listOf(accentSoft, accent) else listOf(accent, accent.copy(alpha = 0.86f)),
                cardBg = theme.cardBackground.toComposeColor(),
                cardBorder = theme.outline.toComposeColor().copy(alpha = 0.5f),
            )
        }

        val safePercent = percent?.coerceIn(0, 100)
        val percentText = safePercent?.let { "$it%" } ?: "准备中…"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.linearGradient(palette.headerGradient))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⬇",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.onAccent,
                            )
                        }
                        Column {
                            Text(
                                text = "正在下载更新",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.onAccent,
                            )
                            Text(
                                text = "请保持网络连接",
                                fontSize = 12.sp,
                                color = palette.onAccent.copy(alpha = 0.85f),
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = percentText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.onAccent,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "正在准备新版本",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = palette.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.cardBg)
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "下载进度",
                            fontSize = 13.sp,
                            color = palette.onSurfaceMuted,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (safePercent == null) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = palette.accent,
                                backgroundColor = palette.outline.copy(alpha = 0.3f),
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = safePercent / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = palette.accent,
                                backgroundColor = palette.outline.copy(alpha = 0.3f),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = percentText,
                            fontSize = 12.sp,
                            color = palette.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Divider(color = palette.outline.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(1.dp, palette.outline.copy(alpha = 0.7f))
                    ) {
                        Text("取消下载", color = palette.onSurface)
                    }
                }
            }
        }
    }

    class DownloadingDialog(owner: MainWindow, title: String) {
        private val messageState = mutableStateOf("准备下载…")
        private val percentState = mutableStateOf<Int?>(null)
        private var onCancel: (() -> Unit)? = null

        private val dialog = JDialog(owner, title, false).apply {
            isResizable = false
            size = Dimension(560, 360)
            setLocationRelativeTo(owner)
        }

        init {
            dialog.contentPane = ComposeHostPanel().apply {
                setContent {
                    DownloadingDialogScreen(
                        message = messageState.value,
                        percent = percentState.value,
                        onCancel = { onCancel?.invoke() },
                    )
                }
            }
        }

        fun setOnCancel(action: (() -> Unit)?) {
            onCancel = action
        }

        fun show() {
            dialog.isVisible = true
        }

        fun close() {
            dialog.isVisible = false
            dialog.dispose()
        }

        fun update(message: String, percent: Int?) {
            SwingUtilities.invokeLater {
                messageState.value = message
                percentState.value = percent
            }
        }
    }
}
