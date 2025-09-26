package editorx.syntax

/**
 * 提供 TokenMaker 类名（用于 RSTA 动态加载）
 */
interface TokenMakerProvider {
    fun getTokenMakerClassName(): String
}