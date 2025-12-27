package editorx.core.plugin

import java.io.File

/**
 * 文件处理器注册表
 * 用于管理所有注册的文件处理器
 */
object FileHandlerRegistry {
    private val fileHandlers = mutableListOf<FileHandler>()
    // 正在处理的文件集合，用于避免重复处理（循环调用）
    private val processingFiles = mutableSetOf<String>()

    /**
     * 注册文件处理器
     * @param handler 文件处理器
     */
    fun register(handler: FileHandler) {
        fileHandlers.add(handler)
    }

    /**
     * 取消注册文件处理器
     * @param handler 文件处理器
     */
    fun unregister(handler: FileHandler) {
        fileHandlers.remove(handler)
    }

    /**
     * 处理文件打开事件
     * 先询问所有注册的文件处理器，如果某个处理器返回 true，则不再继续处理
     * @param file 要打开的文件
     * @return 如果已被处理器处理返回 true，否则返回 false
     */
    fun handleOpenFile(file: File): Boolean {
        // 使用文件的绝对路径作为唯一标识，避免重复处理
        val fileKey = try {
            file.canonicalPath
        } catch (e: Exception) {
            file.absolutePath
        }
        
        // 如果文件正在处理中，直接返回 false，避免循环调用
        if (processingFiles.contains(fileKey)) {
            return false
        }

        for (handler in fileHandlers) {
            if (handler.canHandle(file)) {
                processingFiles.add(fileKey)
                try {
                    val result = handler.handleOpenFile(file)
                    // 如果处理器返回 true，说明已处理，需要延迟移除标记（给异步操作时间）
                    // 如果返回 false，立即移除标记
                    if (result) {
                        // 延迟移除，给异步操作（如对话框）时间完成
                        Thread {
                            Thread.sleep(500)
                            processingFiles.remove(fileKey)
                        }.start()
                    } else {
                        processingFiles.remove(fileKey)
                    }
                    return result
                } catch (e: Exception) {
                    processingFiles.remove(fileKey)
                    throw e
                }
            }
        }
        return false
    }

    /**
     * 获取所有注册的文件处理器
     */
    fun getAllHandlers(): List<FileHandler> {
        return fileHandlers.toList()
    }

    /**
     * 清空所有注册的文件处理器
     */
    fun clear() {
        fileHandlers.clear()
    }
}

