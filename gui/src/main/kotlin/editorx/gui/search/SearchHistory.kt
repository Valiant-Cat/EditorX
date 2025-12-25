package editorx.gui.search

/**
 * 内存中的简单搜索历史，记录最近使用的查询并避免重复。
 */
object SearchHistory {
    private const val MAX_ENTRIES = 20
    private val entries: ArrayDeque<String> = ArrayDeque()

    fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        entries.remove(trimmed)
        entries.addFirst(trimmed)
        while (entries.size > MAX_ENTRIES) {
            entries.removeLastOrNull()
        }
    }

    fun all(): List<String> = entries.toList()
}
