package editorx.core.services

/**
 * 全局搜索/索引服务抽象。
 */
interface SearchService {
    fun search(query: String, limit: Int = 200): List<SearchResult>

    data class SearchResult(
        val filePath: String,
        val lineNumber: Int,
        val preview: String,
    )
}
