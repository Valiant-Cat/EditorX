package editorx.core.plugin

/**
 * 插件生效策略。
 */
enum class PluginRestartPolicy {
    /**
     * 支持热启用/禁用（默认）。
     */
    DYNAMIC,

    /**
     * 需要重启主程序后生效。
     */
    REQUIRES_RESTART,
}
