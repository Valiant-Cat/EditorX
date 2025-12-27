package editorx.core.plugin

/**
 * 插件来源。
 */
enum class PluginOrigin {
    /** 来自应用 classpath（随应用编译/发布）。 */
    SOURCE,

    /** 来自运行时 plugins/ 目录的 JAR。 */
    JAR,
}

