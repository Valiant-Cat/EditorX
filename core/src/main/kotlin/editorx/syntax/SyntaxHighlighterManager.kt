package editorx.syntax

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * 语法高亮管理器
 * 负责管理所有语法高亮提供者
 */
object SyntaxHighlighterManager {
    private val logger = Logger.getLogger(SyntaxHighlighterManager::class.java.name)
    private val highlighterProviders = mutableListOf<SyntaxHighlighterProvider>()
    private val highlighterCache = ConcurrentHashMap<String, SyntaxHighlighter?>()

    /**
     * 注册语法高亮提供者
     */
    fun registerHighlighterProvider(provider: SyntaxHighlighterProvider) {
        highlighterProviders.add(provider)
        logger.info("注册语法高亮提供者: ${provider::class.simpleName}")
    }

    /**
     * 获取所有注册的语法高亮提供者
     */
    fun getHighlighterProviders(): List<SyntaxHighlighterProvider> = highlighterProviders.toList()

    /**
     * 获取文件对应的语法高亮器
     */
    fun getHighlighterForFile(file: File): SyntaxHighlighter? {
        val cacheKey = file.absolutePath
        return highlighterCache.computeIfAbsent(cacheKey) {
            for (provider in highlighterProviders) {
                if (provider.getSelector().matches(file)) {
                    return@computeIfAbsent provider.createHighlighter()
                }
            }
            null
        }
    }
}
