package editorx.core.plugin

/**
 * 插件生命周期状态（简化版）。
 */
enum class PluginState {
    /** 已加载但未启动。 */
    LOADED,

    /** 已启动（activate 已调用）。 */
    STARTED,

    /** 已停止（deactivate 已调用）。 */
    STOPPED,

    /** 启动/停止过程中失败。 */
    FAILED,
}

