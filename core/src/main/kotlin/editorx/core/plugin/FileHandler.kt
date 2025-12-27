package editorx.core.plugin

import java.io.File

/**
 * 文件处理器接口
 * 插件可以实现此接口来处理特定类型的文件打开事件
 */
interface FileHandler {

    /**
     * 检查是否可以处理该文件
     * @param file 要处理的文件
     * @return 如果可以处理返回 true，否则返回 false
     */
    fun canHandle(file: File): Boolean

    /**
     * 处理文件打开事件
     * @param file 要处理的文件
     * @return 如果已处理返回 true，否则返回 false（允许其他处理器继续处理）
     */
    fun handleOpenFile(file: File): Boolean
}

