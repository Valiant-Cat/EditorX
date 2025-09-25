package editorx.event

// Generic app-wide events used for loose coupling between modules and plugins.

sealed interface EditorXEvent

// Plugin lifecycle events
data class PluginLoaded(
    val id: String,
    val name: String,
    val version: String
) : EditorXEvent

data class PluginUnloaded(
    val id: String,
    val name: String
) : EditorXEvent

// Workspace/file events
data class FileOpened(val path: String) : EditorXEvent
data class FileSaved(val path: String) : EditorXEvent
data class ActiveFileChanged(val path: String?) : EditorXEvent

