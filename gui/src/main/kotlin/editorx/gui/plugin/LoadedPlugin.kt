package editorx.gui.plugin

import editorx.plugin.Plugin
import java.io.File
import java.net.URLClassLoader

data class LoadedPlugin(
    val plugin: Plugin,
    val classLoader: URLClassLoader? = null
) {

    /** 插件ID */
    val id: String get() = plugin.getInfo().id

    /** 插件名称 */
    val name: String get() = plugin.getInfo().name

    /** 插件版本 */
    val version: String get() = plugin.getInfo().version
}