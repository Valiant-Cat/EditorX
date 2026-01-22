package editorx.plugins.apk

import java.io.File

object XapkWorkspaceMarker {
    const val MARKER_FILE = ".editorx-xapk"

    fun mark(workspaceRoot: File, originFileName: String?, apkEntries: List<String>) {
        val marker = File(workspaceRoot, MARKER_FILE)
        val content = buildString {
            if (!originFileName.isNullOrBlank()) {
                append("origin=")
                append(originFileName)
                append('\n')
            }
            apkEntries.forEach { entry ->
                append("apk=")
                append(entry)
                append('\n')
            }
        }
        marker.writeText(content)
    }

    fun isXapkWorkspace(workspaceRoot: File): Boolean {
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

    fun readApkEntries(workspaceRoot: File): List<String> {
        val marker = File(workspaceRoot, MARKER_FILE)
        if (!marker.isFile) return emptyList()
        return marker.readLines()
            .filter { it.startsWith("apk=") }
            .mapNotNull { it.removePrefix("apk=").trim().ifEmpty { null } }
    }
}
