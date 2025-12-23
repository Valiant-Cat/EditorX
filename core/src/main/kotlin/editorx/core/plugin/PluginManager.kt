package editorx.core.plugin

import editorx.core.filetype.FileTypeRegistry
import editorx.core.filetype.SyntaxHighlighterRegistry
import editorx.core.i18n.I18n
import editorx.core.plugin.loader.DiscoveredPlugin
import editorx.core.plugin.loader.PluginLoader
import org.slf4j.LoggerFactory
import java.util.IdentityHashMap
import java.util.SortedMap
import java.util.TreeMap


/**
 * 插件管理器
 * 负责插件的发现、加载、启停与卸载，并尽量在卸载时回收资源（注册表、ClassLoader 等）。
 */
class PluginManager {
    companion object {
        private val logger = LoggerFactory.getLogger(PluginManager::class.java)
    }

    interface Listener {
        fun onPluginChanged(pluginId: String) {}
        fun onPluginUnloaded(pluginId: String) {}
    }

    private data class PluginRuntime(
        val plugin: Plugin,
        val context: PluginContextImpl,
        val origin: PluginOrigin,
        val source: java.nio.file.Path?,
        val classLoader: ClassLoader,
        val closeable: AutoCloseable?,
        var state: PluginState,
        var lastError: String?,
    )

    private val pluginsById: SortedMap<String, PluginRuntime> = TreeMap()
    private val contextInitializers: MutableList<(PluginContextImpl) -> Unit> = mutableListOf()
    private val listeners: MutableList<Listener> = mutableListOf()

    // JAR 插件：ClassLoader 需要引用计数，避免一个 JAR 内多个插件时被提前关闭
    private val classLoaderRefCount: IdentityHashMap<ClassLoader, Int> = IdentityHashMap()
    private val classLoaderCloseables: IdentityHashMap<ClassLoader, AutoCloseable> = IdentityHashMap()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * 注册插件上下文初始化器（例如 GUI 层为插件注入 GuiContext）。
     * 会对“已加载”的插件立即执行一次。
     */
    fun registerContextInitializer(initializer: (PluginContextImpl) -> Unit) {
        contextInitializers.add(initializer)
        pluginsById.values.forEach { initializer(it.context) }
    }

    /**
     * 发现并加载全部插件（不自动启动）。
     * 若希望重新扫描，可先调用 [unloadAll] 再调用本方法。
     */
    fun loadAll(pluginLoader: PluginLoader) {
        val discoveredList = pluginLoader.load()
        val closeablesByClassLoader: IdentityHashMap<ClassLoader, AutoCloseable> = IdentityHashMap()
        val loadedCounts: IdentityHashMap<ClassLoader, Int> = IdentityHashMap()

        discoveredList.forEach { discovered ->
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

    fun startAll() {
        pluginsById.keys.toList().forEach { startPlugin(it) }
    }

    fun stopAll() {
        pluginsById.keys.toList().forEach { stopPlugin(it) }
    }

    fun unloadAll() {
        pluginsById.keys.toList().forEach { unloadPlugin(it) }
    }

    fun listPlugins(): List<PluginRecord> {
        return pluginsById.values.map { it.toRecord() }
    }

    fun getPlugin(pluginId: String): PluginRecord? = pluginsById[pluginId]?.toRecord()

    fun startPlugin(pluginId: String) {
        val runtime = pluginsById[pluginId] ?: return
        if (runtime.state == PluginState.STARTED) return

        runCatching {
            runtime.context.active()
            runtime.state = PluginState.STARTED
            runtime.lastError = null
        }.onFailure { e ->
            runtime.state = PluginState.FAILED
            runtime.lastError = e.message ?: e::class.java.simpleName
            cleanupOwner(pluginId)
            logger.warn("插件启动失败: {}", pluginId, e)
        }
        notifyChanged(pluginId)
    }

    fun stopPlugin(pluginId: String) {
        val runtime = pluginsById[pluginId] ?: return
        if (runtime.state != PluginState.STARTED) {
            // 即便不是 STARTED，也允许清理注册表，保证“禁用”效果
            cleanupOwner(pluginId)
            runtime.state = PluginState.STOPPED
            notifyChanged(pluginId)
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
        notifyChanged(pluginId)
    }

    fun unloadPlugin(pluginId: String) {
        val runtime = pluginsById[pluginId] ?: return
        stopPlugin(pluginId)
        pluginsById.remove(pluginId)
        releaseClassLoader(runtime)
        listeners.forEach { it.onPluginUnloaded(pluginId) }
    }

    private fun loadDiscovered(discovered: DiscoveredPlugin): Boolean {
        val plugin = discovered.plugin
        val info = plugin.getInfo()
        val pluginId = info.id

        if (pluginsById.containsKey(pluginId)) {
            logger.warn("忽略重复插件 id：{} (class={})", pluginId, plugin::class.java.name)
            return false
        }

        val context = PluginContextImpl(plugin)
        val runtime = PluginRuntime(
            plugin = plugin,
            context = context,
            origin = discovered.origin,
            source = discovered.source,
            classLoader = discovered.classLoader,
            closeable = discovered.closeable,
            state = PluginState.LOADED,
            lastError = null,
        )
        pluginsById[pluginId] = runtime
        retainClassLoader(runtime)

        contextInitializers.forEach { it(context) }
        notifyChanged(pluginId)
        return true
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

    private fun cleanupOwner(ownerId: String) {
        FileTypeRegistry.unregisterByOwner(ownerId)
        SyntaxHighlighterRegistry.unregisterByOwner(ownerId)
        I18n.unregisterByOwner(ownerId)
    }

    private fun notifyChanged(pluginId: String) {
        listeners.forEach { it.onPluginChanged(pluginId) }
    }

    private fun PluginRuntime.toRecord(): PluginRecord {
        val info = plugin.getInfo()
        return PluginRecord(
            id = info.id,
            name = info.name,
            version = info.version,
            origin = origin,
            state = state,
            source = source,
            lastError = lastError,
        )
    }
}
