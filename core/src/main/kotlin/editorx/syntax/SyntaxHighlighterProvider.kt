package editorx.syntax

/**
 * 语法高亮提供者接口
 * 插件实现此接口来提供自定义语法高亮支持
 */
interface SyntaxHighlighterProvider {
    /**
     * 获取文档选择器
     * 用于确定哪些文件应该使用此高亮器
     */
    fun getSelector(): DocumentSelector

    /**
     * 创建语法高亮器实例
     */
    fun createHighlighter(): SyntaxHighlighter
}
