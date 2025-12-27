package editorx.core.plugin

import java.nio.file.Path

/**
 * 供 UI 展示用的插件快照信息（包含 PluginInfo 和运行时状态）。
 */
data class PluginSnapshot(
    val info: PluginInfo,
    val origin: PluginOrigin,
    val state: PluginState,
    val path: Path?,
    val disabled: Boolean,
)

