package editorx.core.plugin

import editorx.core.gui.GuiExtension
import editorx.core.plugin.loader.PluginLoader
import editorx.core.service.BuildService
import editorx.core.service.MutableServiceRegistry
import org.slf4j.LoggerFactory
import java.util.*

/**
 * 插件管理器
 * 负责插件的发现、加载、按需激活与卸载，并尽量在卸载时回收资源。
 */
class PluginManager {
    companion object {
        private val logger = LoggerFactory.getLogger(PluginManager::class.java)
    }

    interface PluginStateListener {
        fun onPluginStateChanged(pluginId: String) {}
    }

    private data class PluginRuntime(
        val plugin: Plugin,
        val info: PluginInfo,
        val activationEvents: List<ActivationEvent>,
        val context: PluginContext,
        val origin: PluginOrigin,
        val source: java.nio.file.Path?,
        val classLoader: ClassLoader,
        val closeable: AutoCloseable?,
        var state: PluginState,
        var lastError: String?,
    )

    private val servicesRegistry = MutableServiceRegistry()
    private val pluginsById: SortedMap<String, PluginRuntime> = TreeMap()
    private val activationRoutes: MutableMap<ActivationEvent, MutableSet<String>> = mutableMapOf()
    private val disabledPluginIds: MutableSet<String> = linkedSetOf()
    private var guiExtensionFactory: ((PluginContext) -> GuiExtension?)? = null
    private val pluginStateListeners: MutableList<PluginStateListener> = mutableListOf()

    // JAR 插件：ClassLoader 需要引用计数，避免一个 JAR 内多个插件时被提前关闭
    private val classLoaderRefCount: IdentityHashMap<ClassLoader, Int> = IdentityHashMap()
    private val classLoaderCloseables: IdentityHashMap<ClassLoader, AutoCloseable> = IdentityHashMap()

    fun addPluginStateListener(pluginStateListener: PluginStateListener) {
        pluginStateListeners.add(pluginStateListener)
    }

    fun removePluginStateListener(pluginStateListener: PluginStateListener) {
        pluginStateListeners.remove(pluginStateListener)
    }

    fun setInitialDisabled(ids: Collection<String>) {
        disabledPluginIds.clear()
        disabledPluginIds.addAll(ids)
    }

    fun markDisabled(pluginId: String, disabled: Boolean) {
        if (disabled) {
            if (disabledPluginIds.add(pluginId)) {
                stopPlugin(pluginId)
                firePluginStateChanged(pluginId)
            }
        } else {
            if (disabledPluginIds.remove(pluginId)) {
                val runtime = pluginsById[pluginId]
                if (runtime != null) {
                    val shouldAutoStart = runtime.activationEvents.any { it is ActivationEvent.OnStartup } ||
                            activationRoutes.values.any { it.contains(pluginId) }
                    if (shouldAutoStart) {
                        startPlugin(pluginId)
                    }
                }
            }
        }
    }

    /**
     * 设置 GUI Extension 工厂函数。
     * 会对"已加载"的插件立即执行一次。
     */
    fun setGuiExtensionFactory(factory: (PluginContext) -> GuiExtension?) {
        guiExtensionFactory = factory
        pluginsById.values.forEach { ctx ->
            ctx.context.guiExtension = factory(ctx.context)
        }
    }

    /**
     * 发现并加载全部插件（不自动启动）。
     * 若希望重新扫描，可先调用 [unloadAll] 再调用本方法。
     */
    fun scanPlugins(pluginLoader: PluginLoader) {
        val closeablesByClassLoader: IdentityHashMap<ClassLoader, AutoCloseable> = IdentityHashMap()
        val loadedCounts: IdentityHashMap<ClassLoader, Int> = IdentityHashMap()

        pluginLoader.load().forEach { discovered ->
            val closeable = discovered.closeable
            if (closeable != null) {
                closeablesByClassLoader.putIfAbsent(discovered.classLoader, closeable)
            }

            val loaded = loadDiscovered(discovered)
            if (loaded && closeable != null) {
                loadedCounts[discovered.classLoader] = (loadedCounts[discovered.classLoader] ?: 0) + 1
            }
        }

        // 若某个 ClassLoader 的全部插件都因重复 id 等原因未加载，则及时关闭，避免 JAR 句柄泄漏
        closeablesByClassLoader.forEach { (cl, closeable) ->
            if ((loadedCounts[cl] ?: 0) <= 0) {
                runCatching { closeable.close() }
            }
        }
    }

    fun unloadAll() {
        pluginsById.values.filter { it.origin == PluginOrigin.JAR }
            .map { it.info.id }
            .forEach { unloadPlugin(it) }
    }

    fun listPlugins(): List<PluginSnapshot> {
        return pluginsById.values.map { it.toSnapshot(disabledPluginIds.contains(it.info.id)) }
    }

    fun getPlugin(pluginId: String): PluginSnapshot? =
        pluginsById[pluginId]?.toSnapshot(disabledPluginIds.contains(pluginId))

    /**
     * 查找可以为指定工作区提供构建能力的插件
     * @param workspaceRoot 工作区根目录
     * @return 找到的 BuildProvider，如果没有则返回 null
     */
    fun findBuildProvider(workspaceRoot: java.io.File): BuildService? {
        return servicesRegistry.getService(BuildService::class.java)
            .firstOrNull { it.canBuild(workspaceRoot) }
    }

    fun startPlugin(pluginId: String): Boolean {
        val runtime = pluginsById[pluginId] ?: return false
        if (disabledPluginIds.contains(pluginId)) return false
        if (runtime.state == PluginState.STARTED) return true

        cleanupOwner(pluginId)
        return runCatching {
            runtime.context.active()
            runtime.state = PluginState.STARTED
            runtime.lastError = null
            true
        }.onFailure { e ->
            runtime.state = PluginState.FAILED
            runtime.lastError = e.message ?: e::class.java.simpleName
            cleanupOwner(pluginId)
            logger.warn("插件启动失败: {}", pluginId, e)
        }.getOrDefault(false).also {
            if (it) {
                firePluginStateChanged(pluginId)
                logger.info("Plugin started: {}", pluginId)
            }
        }
    }

    fun stopPlugin(pluginId: String) {
        val runtime = pluginsById[pluginId] ?: return
        if (runtime.state != PluginState.STARTED) {
            cleanupOwner(pluginId)
            runtime.state = PluginState.STOPPED
            firePluginStateChanged(pluginId)
            return
        }

        runCatching {
            runtime.context.deactivate()
            runtime.state = PluginState.STOPPED
            runtime.lastError = null
        }.onFailure { e ->
            runtime.state = PluginState.FAILED
            runtime.lastError = e.message ?: e::class.java.simpleName
            logger.warn("插件停止失败: {}", pluginId, e)
        }
        cleanupOwner(pluginId)
        firePluginStateChanged(pluginId)
        logger.info("Plugin stopped: {}", pluginId)
    }

    /**
     * 卸载插件。
     * @return 是否成功卸载。内置插件（SOURCE）不可卸载，将返回 false。
     */
    fun unloadPlugin(pluginId: String): Boolean {
        val runtime = pluginsById[pluginId] ?: return false

        // 内置插件（随源码一起打包的）不可卸载
        if (runtime.origin == PluginOrigin.SOURCE) {
            logger.warn("内置插件不可卸载: {}", pluginId)
            return false
        }

        stopPlugin(pluginId)
        pluginsById.remove(pluginId)
        activationRoutes.values.forEach { it.remove(pluginId) }
        releaseClassLoader(runtime)
        logger.info("Plugin unloaded: {}", pluginId)
        return true
    }

    fun triggerStartup() {
        triggerEvent(ActivationEvent.OnStartup)
    }

    fun triggerCommand(commandId: String) {
        triggerEvent(ActivationEvent.OnCommand(commandId))
    }

    private fun triggerEvent(event: ActivationEvent) {
        val targets = activationRoutes[event]?.toList() ?: return
        targets.forEach { pluginId ->
            val started = startPlugin(pluginId)
            if (started) {
                activationRoutes[event]?.remove(pluginId)
            }
        }
    }

    private fun loadDiscovered(discovered: LoadedPlugin): Boolean {
        val plugin = discovered.plugin
        val info = plugin.getInfo()
        val pluginId = info.id

        if (pluginsById.containsKey(pluginId)) {
            logger.warn("忽略重复插件 id：{} (class={})", pluginId, plugin::class.java.name)
            return false
        }
        val events = plugin.activationEvents().ifEmpty { listOf(ActivationEvent.OnStartup) }
        val pluginContext = PluginContext(plugin, servicesRegistry)
        val initialState = if (disabledPluginIds.contains(pluginId)) {
            PluginState.STOPPED
        } else {
            PluginState.LOADED
        }
        val runtime = PluginRuntime(
            plugin = plugin,
            info = info,
            activationEvents = events,
            context = pluginContext,
            origin = discovered.origin,
            source = discovered.path,
            classLoader = discovered.classLoader,
            closeable = discovered.closeable,
            state = initialState,
            lastError = null,
        )
        pluginsById[pluginId] = runtime
        retainClassLoader(runtime)

        registerActivation(pluginId, events)
        pluginContext.guiExtension = guiExtensionFactory?.invoke(pluginContext)
        firePluginStateChanged(pluginId)
        return true
    }

    private fun registerActivation(pluginId: String, events: List<ActivationEvent>) {
        val normalized = events.ifEmpty { listOf(ActivationEvent.OnStartup) }
        normalized.forEach { event ->
            if (event == ActivationEvent.OnDemand) return@forEach
            activationRoutes.getOrPut(event) { linkedSetOf() }.add(pluginId)
        }
    }

    private fun retainClassLoader(runtime: PluginRuntime) {
        val closeable = runtime.closeable ?: return
        val cl = runtime.classLoader
        classLoaderRefCount[cl] = (classLoaderRefCount[cl] ?: 0) + 1
        classLoaderCloseables.putIfAbsent(cl, closeable)
    }

    private fun releaseClassLoader(runtime: PluginRuntime) {
        val closeable = runtime.closeable ?: return
        val cl = runtime.classLoader
        val next = (classLoaderRefCount[cl] ?: 1) - 1
        if (next <= 0) {
            classLoaderRefCount.remove(cl)
            val c = classLoaderCloseables.remove(cl) ?: closeable
            runCatching { c.close() }
        } else {
            classLoaderRefCount[cl] = next
        }
    }

    @Deprecated("移步到Plugin.deactivate()")
    private fun cleanupOwner(ownerId: String) {
    }

    private fun firePluginStateChanged(pluginId: String) {
        pluginStateListeners.forEach { it.onPluginStateChanged(pluginId) }
    }

    private fun PluginRuntime.toSnapshot(disabled: Boolean): PluginSnapshot =
        PluginSnapshot(
            info = info,
            origin = origin,
            state = state,
            path = source,
            disabled = disabled,
        )
}
