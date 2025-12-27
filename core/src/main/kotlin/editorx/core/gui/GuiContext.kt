package editorx.core.gui

import editorx.core.plugin.PluginManager
import editorx.core.util.Store
import editorx.core.workspace.Workspace
import java.io.File

/**
 * GUI 上下文接口
 * 提供 GUI 应用所需的基础设施
 */
interface GuiContext {

    /**
     * 获取应用目录
     */
    fun getAppDir(): File

    /**
     * 获取设置存储
     */
    fun getSettings(): Store

    /**
     * 获取工作区
     */
    fun getWorkspace(): Workspace
    
    /**
     * 获取插件管理器
     */
    fun getPluginManager(): PluginManager
}
