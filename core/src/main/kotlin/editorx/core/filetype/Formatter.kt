package editorx.core.filetype

/**
 * 代码格式化器接口
 * 插件可以实现此接口来提供特定文件类型的格式化功能
 */
interface Formatter {
    /**
     * 格式化代码
     * @param content 原始代码内容
     * @return 格式化后的代码内容
     * @throws Exception 如果格式化失败（如代码格式错误）
     */
    fun format(content: String): String
}

