package editorx.plugins.aar

import java.io.File

object AarWorkspaceMarker {
    const val MARKER_FILE = ".editorx-aar"

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

    fun isAarWorkspace(workspaceRoot: File): Boolean {
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
