package editorx.core.service

import java.io.File

/**
 * 构建提供者接口
 * 插件可以实现此接口来为特定类型的项目提供构建能力
 */
interface BuildService {
    /**
     * 检查工作区是否可以被此提供者构建
     * @param workspaceRoot 工作区根目录
     * @return 如果此提供者可以构建该工作区，返回 true
     */
    fun canBuild(workspaceRoot: File): Boolean

    /**
     * 执行构建
     * @param workspaceRoot 工作区根目录
     * @param onProgress 进度回调，参数为进度消息
     * @return 构建结果
     */
    fun build(workspaceRoot: File, onProgress: (String) -> Unit): BuildResult
}

/**
 * 构建结果
 */
data class BuildResult(
    /**
     * 构建状态
     */
    val status: BuildStatus,
    /**
     * 构建输出的文件（如果成功）
     */
    val outputFile: File? = null,
    /**
     * 错误消息（如果失败）
     */
    val errorMessage: String? = null,
    /**
     * 退出码（如果可用）
     */
    val exitCode: Int? = null,
    /**
     * 构建输出日志
     */
    val output: String? = null
)

/**
 * 构建状态
 */
enum class BuildStatus {
    /**
     * 构建成功
     */
    SUCCESS,

    /**
     * 构建失败
     */
    FAILED,

    /**
     * 构建工具未找到
     */
    NOT_FOUND,

    /**
     * 构建被取消
     */
    CANCELLED
}

