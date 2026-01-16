package editorx.gui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.PluginManager
import editorx.core.plugin.PluginOrigin
import editorx.core.plugin.PluginSnapshot
import editorx.core.plugin.PluginState
import editorx.core.plugin.loader.DuplexPluginLoader
import editorx.core.util.AppPaths
import editorx.core.util.Store
import editorx.gui.compose.toComposeColor
import editorx.gui.settings.SettingsStoreKeys
import editorx.gui.theme.Theme
import java.awt.Desktop
import java.awt.Dialog
import java.awt.FileDialog
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PluginsSettingsPanel(
    theme: Theme,
    settings: Store,
    pluginManager: PluginManager,
    parentDialog: Dialog,
    showAlert: (SettingsAlertRequest) -> Unit,
    coroutineScope: CoroutineScope,
) {
    val onSurface = theme.onSurface.toComposeColor()
    val onSurfaceMuted = theme.onSurfaceVariant.toComposeColor()
    val outline = theme.outline.toComposeColor()
    val surfaceVariant = theme.surfaceVariant.toComposeColor()

    var snapshots by remember { mutableStateOf(pluginManager.listPlugins()) }
    var selectedId by remember { mutableStateOf(snapshots.firstOrNull()?.info?.id) }
    var statusText by remember { mutableStateOf(" ") }

    DisposableEffect(Unit) {
        val listener = object : PluginManager.PluginStateListener {
            override fun onPluginStateChanged(pluginId: String) {
                SwingUtilities.invokeLater {
                    snapshots = pluginManager.listPlugins()
                    selectedId = pluginId
                }
            }
        }
        pluginManager.addPluginStateListener(listener)
        onDispose { pluginManager.removePluginStateListener(listener) }
    }

    fun refresh() {
        snapshots = pluginManager.listPlugins()
        if (selectedId !in snapshots.map { it.info.id }) {
            selectedId = snapshots.firstOrNull()?.info?.id
        }
    }

    fun persistDisabledSet() {
        val disabledIds = pluginManager.listPlugins().filter { it.disabled }.map { it.info.id }
        if (disabledIds.isEmpty()) {
            settings.remove(SettingsStoreKeys.DISABLED_PLUGINS)
        } else {
            settings.put(SettingsStoreKeys.DISABLED_PLUGINS, disabledIds.joinToString(","))
        }
        settings.sync()
    }

    fun enable(snapshot: PluginSnapshot) {
        pluginManager.markDisabled(snapshot.info.id, false)
        pluginManager.startPlugin(snapshot.info.id)
        persistDisabledSet()
        statusText = I18n.translate(I18nKeys.Plugins.ENABLED).format(snapshot.info.id)
        refresh()
    }

    fun disable(snapshot: PluginSnapshot) {
        pluginManager.markDisabled(snapshot.info.id, true)
        persistDisabledSet()
        statusText = I18n.translate(I18nKeys.Plugins.DISABLED).format(snapshot.info.id)
        refresh()
    }

    fun uninstall(snapshot: PluginSnapshot) {
        if (snapshot.origin == PluginOrigin.SOURCE) {
            showAlert(
                SettingsAlertRequest(
                    title = I18n.translate(I18nKeys.Dialog.TIP),
                    message = I18n.translate(I18nKeys.Plugins.BUILTIN_CANNOT_UNINSTALL)
                        .format(snapshot.info.name, snapshot.info.id),
                    confirmText = I18n.translate(I18nKeys.Action.CONFIRM)
                )
            )
            return
        }

        showAlert(
            SettingsAlertRequest(
                title = I18n.translate(I18nKeys.Dialog.CONFIRM_REMOVAL),
                message = I18n.translate(I18nKeys.Plugins.CONFIRM_UNINSTALL)
                    .format(snapshot.info.name, snapshot.info.id),
                confirmText = I18n.translate(I18nKeys.Action.CONFIRM),
                dismissText = I18n.translate(I18nKeys.Action.CANCEL),
                onConfirm = {
                    val success = pluginManager.unloadPlugin(snapshot.info.id)
                    if (success) {
                        persistDisabledSet()
                        statusText = I18n.translate(I18nKeys.Plugins.REMOVED).format(snapshot.info.id)
                        refresh()
                    } else {
                        showAlert(
                            SettingsAlertRequest(
                                title = I18n.translate(I18nKeys.Dialog.ERROR),
                                message = I18n.translate(I18nKeys.Plugins.UNINSTALL_FAILED)
                                    .format(snapshot.info.name, snapshot.info.id),
                                confirmText = I18n.translate(I18nKeys.Action.CONFIRM)
                            )
                        )
                    }
                }
            )
        )
    }

    fun openPluginDir() {
        val dir = AppPaths.pluginsDir().toFile()
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

    fun installPluginJar() {
        val chooser = FileDialog(parentDialog, I18n.translate(I18nKeys.Dialog.SELECT_PLUGIN_JAR), FileDialog.LOAD)
        chooser.isVisible = true
        val fileName = chooser.file ?: return
        val dirName = chooser.directory ?: return
        val selected = File(dirName, fileName)
        if (!selected.isFile || !selected.name.endsWith(".jar", ignoreCase = true)) {
            showAlert(
                SettingsAlertRequest(
                    title = I18n.translate(I18nKeys.Dialog.TIP),
                    message = I18n.translate(I18nKeys.Dialog.SELECT_JAR_FILE),
                    confirmText = I18n.translate(I18nKeys.Action.CONFIRM)
                )
            )
            return
        }

        val pluginDir = AppPaths.pluginsDir()
        runCatching { Files.createDirectories(pluginDir) }
        val target = pluginDir.resolve(selected.name)

        fun doCopy(overwrite: Boolean) {
            coroutineScope.launch {
                val before = pluginManager.listPlugins().map { it.info.id }.toSet()
                val copyError = withContext(Dispatchers.IO) {
                    runCatching {
                        if (overwrite) {
                            Files.copy(selected.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
                        } else {
                            Files.copy(selected.toPath(), target)
                        }
                    }.exceptionOrNull()
                }
                if (copyError != null) {
                    showAlert(
                        SettingsAlertRequest(
                            title = I18n.translate(I18nKeys.Dialog.ERROR),
                            message = I18n.translate(I18nKeys.Dialog.COPY_FAILED)
                                .format(copyError.message ?: copyError.toString()),
                            confirmText = I18n.translate(I18nKeys.Action.CONFIRM)
                        )
                    )
                    return@launch
                }

                pluginManager.scanPlugins(DuplexPluginLoader())
                val newSnapshots = pluginManager.listPlugins().filterNot { before.contains(it.info.id) }
                newSnapshots.forEach { pluginManager.startPlugin(it.info.id) }
                persistDisabledSet()
                refresh()

                statusText = if (newSnapshots.isEmpty()) {
                    I18n.translate(I18nKeys.Plugins.NO_PLUGIN_ENTRY)
                } else {
                    I18n.translate(I18nKeys.Plugins.INSTALLED_AND_STARTED)
                        .format(newSnapshots.joinToString(", ") { it.info.id })
                }
            }
        }

        if (Files.exists(target)) {
            showAlert(
                SettingsAlertRequest(
                    title = I18n.translate(I18nKeys.Dialog.TIP),
                    message = buildString {
                        append(I18n.translate(I18nKeys.Dialog.CONFIRM_OVERWRITE).format(target.fileName.toString()))
                        append("\n\n")
                        append(target.toAbsolutePath().toString())
                    },
                    confirmText = I18n.translate(I18nKeys.Action.CONFIRM),
                    dismissText = I18n.translate(I18nKeys.Action.CANCEL),
                    onConfirm = { doCopy(overwrite = true) }
                )
            )
        } else {
            doCopy(overwrite = false)
        }
    }

    val selected = snapshots.firstOrNull { it.info.id == selectedId }
    val listScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = I18n.translate(I18nKeys.Settings.PLUGINS),
            color = onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { installPluginJar() }) {
                Text(I18n.translate(I18nKeys.Action.INSTALL_PLUGIN))
            }
            OutlinedButton(onClick = { openPluginDir() }) {
                Text(I18n.translate(I18nKeys.Action.OPEN_PLUGINS_FOLDER))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = I18n.translate(I18nKeys.Plugins.PLUGINS_COUNT).format(snapshots.size),
                color = onSurfaceMuted,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, outline, RoundedCornerShape(10.dp))
        ) {
            // 左侧列表
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .background(surfaceVariant)
                    .verticalScroll(listScroll)
            ) {
                snapshots.forEach { snap ->
                    val selectedRow = snap.info.id == selectedId
                    val bg = if (selectedRow) theme.primaryContainer.toComposeColor() else surfaceVariant
                    val fg = if (selectedRow) theme.onPrimaryContainer.toComposeColor() else onSurface
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .clickable { selectedId = snap.info.id }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${snap.info.name}  (${displayPluginState(snap)})",
                            color = fg,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Divider(color = outline.copy(alpha = 0.6f))
                }
            }

            Divider(color = outline, modifier = Modifier.fillMaxHeight().width(1.dp))

            // 右侧详情
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selected == null) {
                    Text(I18n.translate(I18nKeys.Plugins.NO_PLUGIN_SELECTED), color = onSurfaceMuted)
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        text = "${selected.info.name} (${selected.info.id})",
                        color = onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text("${I18n.translate(I18nKeys.Plugins.ID)}: ${selected.info.id}", color = onSurface, fontSize = 12.sp)
                    Text("${I18n.translate(I18nKeys.Plugins.VERSION)}: ${selected.info.version}", color = onSurface, fontSize = 12.sp)
                    Text("${I18n.translate(I18nKeys.Plugins.ORIGIN)}: ${formatOrigin(selected.origin)}", color = onSurface, fontSize = 12.sp)
                    Text("${I18n.translate(I18nKeys.Plugins.STATE)}: ${displayPluginState(selected)}", color = onSurface, fontSize = 12.sp)
                    Text(
                        text = "${I18n.translate(I18nKeys.Plugins.PATH)}:\n${selected.path?.toString() ?: "-"}",
                        color = onSurfaceMuted,
                        fontSize = 12.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.weight(1f))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { enable(selected) },
                            enabled = selected.state != PluginState.STARTED || selected.disabled
                        ) {
                            Text(I18n.translate(I18nKeys.Action.ENABLE))
                        }
                        OutlinedButton(
                            onClick = { disable(selected) },
                            enabled = !selected.disabled
                        ) {
                            Text(I18n.translate(I18nKeys.Action.DISABLE))
                        }
                        OutlinedButton(
                            onClick = { uninstall(selected) },
                            enabled = selected.origin != PluginOrigin.SOURCE
                        ) {
                            Text(I18n.translate(I18nKeys.Action.UNINSTALL))
                        }
                    }
                }

                Divider(color = outline)
                Text(
                    text = statusText,
                    color = onSurfaceMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun displayPluginState(snapshot: PluginSnapshot): String {
    return when {
        snapshot.disabled -> I18n.translate(I18nKeys.Settings.PLUGIN_STATE_DISABLED)
        snapshot.state == PluginState.STARTED -> I18n.translate(I18nKeys.Settings.PLUGIN_STATE_ENABLED)
        snapshot.state == PluginState.FAILED -> I18n.translate(I18nKeys.Settings.PLUGIN_STATE_FAILED)
        else -> snapshot.state.name
    }
}

private fun formatOrigin(origin: PluginOrigin): String {
    return when (origin) {
        PluginOrigin.SOURCE -> I18n.translate(I18nKeys.Plugins.BUNDLED)
        PluginOrigin.JAR -> "JAR"
    }
}

