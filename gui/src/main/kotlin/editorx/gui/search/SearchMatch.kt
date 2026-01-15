package editorx.gui.search

import java.io.File

enum class SearchMatchType(val displayName: String) {
    CLASS("类"),
    METHOD("方法"),
    FIELD("字段"),
    CODE("代码"),
    RESOURCE("资源"),
    UNKNOWN("代码"),
}

data class SearchMatch(
    val file: File,
    /** 1-based 行号 */
    val line: Int,
    /** 0-based 列号 */
    val column: Int,
    val length: Int,
    val preview: String,
    val type: SearchMatchType = SearchMatchType.UNKNOWN,
)
