package editorx.gui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import editorx.core.gui.GuiContext
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.gui.compose.toComposeColor
import editorx.gui.theme.Theme
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CacheSettingsPanel(
    theme: Theme,
    environment: GuiContext,
    showAlert: (SettingsAlertRequest) -> Unit,
    coroutineScope: CoroutineScope,
) {
    val onSurface = theme.onSurface.toComposeColor()
    val onSurfaceMuted = theme.onSurfaceVariant.toComposeColor()
    val outline = theme.outline.toComposeColor()
    val surfaceVariant = theme.surfaceVariant.toComposeColor()

    data class CacheEntry(
        val nameKey: String,
        val descKey: String,
        val dir: File,
    )

    val appDir = remember { environment.getAppDir() }
    val entries = remember(appDir) {
        listOf(
            CacheEntry(I18nKeys.Cache.CACHE_CONTENT, I18nKeys.Cache.CACHE_DESC, File(appDir, "cache")),
            CacheEntry(I18nKeys.Cache.LOGS, I18nKeys.Cache.LOGS_DESC, File(appDir, "logs")),
        )
    }

    var selectedIndex by remember { mutableStateOf(-1) }
    var sizes by remember { mutableStateOf<List<Long>>(entries.map { 0L }) }
    var loading by remember { mutableStateOf(true) }

    fun refreshSizes() {
        coroutineScope.launch {
            loading = true
            val computed = withContext(Dispatchers.IO) {
                entries.map { computeSize(it.dir) }
            }
            sizes = computed
            loading = false
        }
    }

    LaunchedEffect(Unit) { refreshSizes() }

    fun openSelected() {
        val idx = selectedIndex.takeIf { it in entries.indices } ?: run {
            showAlert(
                SettingsAlertRequest(
                    title = I18n.translate(I18nKeys.Dialog.INFO),
                    message = I18n.translate(I18nKeys.Dialog.SELECT_ENTRY_FIRST),
                    confirmText = I18n.translate(I18nKeys.Action.CONFIRM)
                )
            )
            return
        }
        val dir = entries[idx].dir
        if (!dir.exists()) dir.mkdirs()
        runCatching { Desktop.getDesktop().open(dir) }
            .onFailure {
                showAlert(
                    SettingsAlertRequest(
                        title = I18n.translate(I18nKeys.Dialog.INFO),
                        message = I18n.translate(I18nKeys.Dialog.UNABLE_TO_OPEN).format(dir.absolutePath),
                        confirmText = I18n.translate(I18nKeys.Action.CONFIRM)
                    )
                )
            }
    }

    fun clearSelected() {
        val idx = selectedIndex.takeIf { it in entries.indices } ?: run {
            showAlert(
                SettingsAlertRequest(
                    title = I18n.translate(I18nKeys.Dialog.INFO),
                    message = I18n.translate(I18nKeys.Dialog.SELECT_ENTRY_FIRST),
                    confirmText = I18n.translate(I18nKeys.Action.CONFIRM)
                )
            )
            return
        }

        val entry = entries[idx]
        if (!entry.dir.exists()) {
            showAlert(
                SettingsAlertRequest(
                    title = I18n.translate(I18nKeys.Dialog.INFO),
                    message = I18n.translate(I18nKeys.Dialog.DIRECTORY_NOT_FOUND).format(entry.dir.absolutePath),
                    confirmText = I18n.translate(I18nKeys.Action.CONFIRM)
                )
            )
            refreshSizes()
            return
        }

        showAlert(
            SettingsAlertRequest(
                title = I18n.translate(I18nKeys.Dialog.CLEAR_CACHE),
                message = I18n.translate(I18nKeys.Cache.CONFIRM_CLEAR)
                    .format(I18n.translate(entry.nameKey), entry.dir.absolutePath),
                confirmText = I18n.translate(I18nKeys.Action.CONFIRM),
                dismissText = I18n.translate(I18nKeys.Action.CANCEL),
                onConfirm = {
                    coroutineScope.launch {
                        val success = withContext(Dispatchers.IO) { deleteRecursively(entry.dir) }
                        if (!success) {
                            showAlert(
                                SettingsAlertRequest(
                                    title = I18n.translate(I18nKeys.Dialog.ERROR),
                                    message = I18n.translate(I18nKeys.Cache.CANNOT_DELETE).format(entry.dir.absolutePath),
                                    confirmText = I18n.translate(I18nKeys.Action.CONFIRM)
                                )
                            )
                        }
                        refreshSizes()
                    }
                }
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = I18n.translate(I18nKeys.Settings.CACHE_TITLE),
            color = onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = I18n.translate(I18nKeys.Settings.CACHE_HINT),
            color = onSurfaceMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { refreshSizes() }) {
                Text(I18n.translate(I18nKeys.Settings.REFRESH_CACHE))
            }
            OutlinedButton(onClick = { clearSelected() }) {
                Text(I18n.translate(I18nKeys.Settings.CLEAR_SELECTED))
            }
            OutlinedButton(onClick = { openSelected() }) {
                Text(I18n.translate(I18nKeys.Settings.OPEN_FOLDER))
            }
            Spacer(Modifier.weight(1f))
            if (loading) {
                Text("…", color = onSurfaceMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, outline, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        I18n.translate(I18nKeys.CacheTable.NAME),
                        color = onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.20f)
                    )
                    Text(
                        I18n.translate(I18nKeys.CacheTable.PATH),
                        color = onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.42f)
                    )
                    Text(
                        I18n.translate(I18nKeys.CacheTable.SIZE),
                        color = onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.14f)
                    )
                    Text(
                        I18n.translate(I18nKeys.CacheTable.DESCRIPTION),
                        color = onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.24f)
                    )
                }
                Divider(color = outline)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    entries.forEachIndexed { idx, entry ->
                        val selected = idx == selectedIndex
                        val bg = if (selected) theme.primaryContainer.toComposeColor() else theme.surface.toComposeColor()
                        val fg = if (selected) theme.onPrimaryContainer.toComposeColor() else onSurface
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg)
                                .clickable { selectedIndex = idx }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                I18n.translate(entry.nameKey),
                                color = fg,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(0.20f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                entry.dir.absolutePath,
                                color = fg,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(0.42f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                readableSize(sizes.getOrNull(idx) ?: 0L),
                                color = fg,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(0.14f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                I18n.translate(entry.descKey),
                                color = fg.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(0.24f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (idx < entries.lastIndex) {
                            Divider(color = outline.copy(alpha = 0.65f))
                        }
                    }
                }
            }
        }
    }
}

private fun computeSize(dir: File): Long {
    if (!dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    val children = dir.listFiles() ?: return 0L
    var total = 0L
    for (child in children) {
        total += computeSize(child)
    }
    return total
}

private fun deleteRecursively(file: File): Boolean {
    if (!file.exists()) return true
    if (file.isDirectory) {
        file.listFiles()?.forEach { child ->
            if (!deleteRecursively(child)) return false
        }
    }
    return runCatching { file.delete() }.getOrDefault(false)
}

private fun readableSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1fKB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1fMB", mb)
    val gb = mb / 1024.0
    return String.format("%.1fGB", gb)
}

