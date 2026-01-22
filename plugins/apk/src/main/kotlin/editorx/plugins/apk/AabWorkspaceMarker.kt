package editorx.plugins.apk

import java.io.File

object AabWorkspaceMarker {
    const val MARKER_FILE = ".editorx-aab"

    fun mark(workspaceRoot: File, originFileName: String?) {
        val marker = File(workspaceRoot, MARKER_FILE)
        val content = buildString {
            if (!originFileName.isNullOrBlank()) {
                append("origin=")
                append(originFileName)
                append('\n')
            }
        }
        marker.writeText(content)
    }

    fun isAabWorkspace(workspaceRoot: File): Boolean {
        return File(workspaceRoot, MARKER_FILE).isFile
    }

    fun readOriginFileName(workspaceRoot: File): String? {
        val marker = File(workspaceRoot, MARKER_FILE)
        if (!marker.isFile) return null
        return marker.readLines()
            .firstOrNull { it.startsWith("origin=") }
            ?.removePrefix("origin=")
            ?.trim()
            ?.ifEmpty { null }
    }
}
