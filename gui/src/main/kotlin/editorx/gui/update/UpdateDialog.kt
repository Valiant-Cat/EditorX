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
        val hasNotes = notes != "暂无更新说明" && notes.isNotBlank()
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
        // 根据是否有更新说明调整弹窗高度
        dialog.size = Dimension(720, if (hasNotes) 560 else 380)
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
            val isDark = theme is Theme.Dark
            val onAccent = if (isDark) theme.onPrimaryContainer.toComposeColor() else theme.onPrimary.toComposeColor()
            UpdatePalette(
                surface = theme.surface.toComposeColor(),
                outline = theme.outline.toComposeColor(),
                onSurface = theme.onSurface.toComposeColor(),
                onSurfaceMuted = theme.onSurfaceVariant.toComposeColor(),
                accent = accent,
                accentSoft = theme.primaryContainer.toComposeColor(),
                onAccent = onAccent,
                headerGradient = emptyList(), // 不再使用渐变
                noteBg = theme.cardBackground.toComposeColor(),
                noteBorder = theme.outline.toComposeColor().copy(alpha = 0.2f),
                badgeBg = if (isDark) accent.copy(alpha = 0.12f) else accent.copy(alpha = 0.08f),
                badgeText = accent,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.surface)
        ) {
            // 简化的顶部区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 32.dp)
            ) {
                // 标题行 - NEW标签在标题旁
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // NEW 标签
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.badgeBg)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "NEW",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.badgeText,
                            letterSpacing = 0.5.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "发现新版本",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = palette.onSurface,
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 版本信息
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当前版本 ${info.currentVersion}",
                        fontSize = 13.sp,
                        color = palette.onSurfaceMuted,
                    )
                    Text(
                        text = "→",
                        fontSize = 13.sp,
                        color = palette.onSurfaceMuted.copy(alpha = 0.4f),
                    )
                    Text(
                        text = info.latestVersion,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.accent,
                    )
                }
            }

            // 内容区域
            val hasNotes = notes != "暂无更新说明" && notes.isNotBlank()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 32.dp,
                        vertical = if (hasNotes) 24.dp else 20.dp
                    )
            ) {
                if (hasNotes) {

                    val scroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.noteBg)
                            .border(1.dp, palette.noteBorder, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                            .verticalScroll(scroll)
                    ) {
                        Text(
                            text = notes,
                            fontSize = 13.sp,
                            color = palette.onSurface,
                            lineHeight = 20.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                } else {
                    // 空状态 - 不显示大块空白区域，直接显示提示
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(if (hasNotes) 24.dp else 20.dp))

                // 按钮区域 - 更简洁的样式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(0.5.dp, palette.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Text(
                            "取消",
                            fontSize = 13.sp,
                            color = palette.onSurface,
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = palette.accent,
                            contentColor = palette.onAccent,
                        ),
                    ) {
                        Text(
                            "更新并重启",
                            fontSize = 13.sp,
                        )
                    }
                }
            }
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
        downloadedBytes: Long?,
        totalBytes: Long?,
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
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    onCancel = onCancel,
                )
            }
        }
    }

    @Composable
    private fun DownloadingDialogContent(
        theme: Theme,
        downloadedBytes: Long?,
        totalBytes: Long?,
        onCancel: () -> Unit,
    ) {
        val palette = remember(theme) {
            val accent = theme.primary.toComposeColor()
            val isDark = theme is Theme.Dark
            val onAccent = if (isDark) theme.onPrimaryContainer.toComposeColor() else theme.onPrimary.toComposeColor()
            DownloadPalette(
                surface = theme.surface.toComposeColor(),
                onSurface = theme.onSurface.toComposeColor(),
                onSurfaceMuted = theme.onSurfaceVariant.toComposeColor(),
                outline = theme.outline.toComposeColor(),
                accent = accent,
                accentSoft = theme.primaryContainer.toComposeColor(),
                onAccent = onAccent,
                headerGradient = emptyList(), // 不再使用渐变
                cardBg = theme.cardBackground.toComposeColor(),
                cardBorder = theme.outline.toComposeColor().copy(alpha = 0.2f),
            )
        }

        // 计算进度百分比
        val progress = remember(downloadedBytes, totalBytes) {
            if (downloadedBytes != null && totalBytes != null && totalBytes > 0) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else null
        }

        // 格式化左侧文本：下载进度（百分比）
        val leftText = remember(progress) {
            val percentText = progress?.let { "${(it * 100).toInt()}%" } ?: ""
            if (percentText.isNotEmpty()) {
                "下载进度（$percentText）"
            } else {
                "下载进度"
            }
        }

        // 格式化右侧文本：文件大小
        val rightText = remember(downloadedBytes, totalBytes) {
            when {
                downloadedBytes != null && totalBytes != null -> {
                    "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
                }
                downloadedBytes != null -> {
                    formatBytes(downloadedBytes)
                }
                else -> "准备中…"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.surface)
        ) {
            // 简化的顶部区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 32.dp)
            ) {
                Text(
                    text = "正在下载更新",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = palette.onSurface,
                )
            }

            // 内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                // 进度条区域
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = leftText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.onSurface,
                        )
                        Text(
                            text = rightText,
                            fontSize = 13.sp,
                            color = palette.accent,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 进度条
                    if (progress == null) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = palette.accent,
                            backgroundColor = palette.outline.copy(alpha = 0.2f),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = palette.accent,
                            backgroundColor = palette.outline.copy(alpha = 0.2f),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(0.5.dp, palette.outline.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            "取消下载",
                            fontSize = 13.sp,
                            color = palette.onSurface,
                        )
                    }
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes B"
        }
    }

    class DownloadingDialog(owner: MainWindow, title: String) {
        private val downloadedBytesState = mutableStateOf<Long?>(null)
        private val totalBytesState = mutableStateOf<Long?>(null)
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
                        downloadedBytes = downloadedBytesState.value,
                        totalBytes = totalBytesState.value,
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

        fun update(downloadedBytes: Long?, totalBytes: Long?) {
            SwingUtilities.invokeLater {
                downloadedBytesState.value = downloadedBytes
                totalBytesState.value = totalBytes
            }
        }
    }
}
