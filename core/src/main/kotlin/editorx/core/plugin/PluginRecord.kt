package editorx.core.plugin

import java.nio.file.Path

/**
 * 供 UI 展示用的插件快照信息（避免暴露内部运行时对象）。
 */
data class PluginRecord(
    val id: String,
    val name: String,
    val version: String,
    val origin: PluginOrigin,
    val state: PluginState,
    val source: Path?,
    val lastError: String?,
)

