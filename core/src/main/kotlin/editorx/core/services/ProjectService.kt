package editorx.core.services

import java.io.File

/**
 * 项目加载与索引服务抽象。
 */
interface ProjectService {
    fun openProject(source: File)
    fun closeProject()
    fun currentProject(): File?
}
