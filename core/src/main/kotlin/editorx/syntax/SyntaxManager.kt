package editorx.syntax

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 语法高亮管理器
 * 负责管理所有语法高亮提供者
 */
object SyntaxManager {
    private val adapters = mutableListOf<SyntaxProvider>()
    private val fileToAdapterCache = ConcurrentHashMap<String, SyntaxProvider?>()

    /**
     * 注册语法适配器
     */
    fun registerAdapter(adapter: SyntaxProvider) {
        adapters.add(adapter)

        // 注册 TokenMaker
        val tmf = TokenMakerFactory.getDefaultInstance() as AbstractTokenMakerFactory
        tmf.putMapping(adapter.syntaxStyleKey, adapter.getTokenMakerClassName())
    }

    /**
     * 获取文件对应的语法适配器
     */
    fun getAdapterForFile(file: File): SyntaxProvider? {
        val cacheKey = file.absolutePath
        return fileToAdapterCache.computeIfAbsent(cacheKey) {
            val ext = file.extension.lowercase()
            return@computeIfAbsent adapters.find { ext in it.fileExtensions }
        }
    }
}
