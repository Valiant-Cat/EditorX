package editorx.gui.search

import java.io.File

data class SearchMatch(
    val file: File,
    /** 1-based 行号 */
    val line: Int,
    /** 0-based 列号 */
    val column: Int,
    val length: Int,
    val preview: String,
)

