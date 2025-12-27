# BuildProvider 架构设计分析

## 当前实现方式

### 架构概览

1. **接口设计**：`BuildProvider` 是一个独立的接口，插件通过实现 `Plugin` 和 `BuildProvider` 两个接口来提供构建能力
2. **查找机制**：`PluginManager.findBuildProvider()` 通过遍历所有已启动的插件，使用类型检查 (`plugin is BuildProvider`) 来查找提供者
3. **匹配逻辑**：使用 `firstOrNull { it.canBuild(workspaceRoot) }` 找到第一个能处理当前工作区的提供者

### 代码示例

```kotlin
fun findBuildProvider(workspaceRoot: File): BuildProvider? {
    return pluginsById.values
        .filter { it.state == PluginState.STARTED }
        .mapNotNull { runtime ->
            val plugin = runtime.plugin
            if (plugin is BuildProvider) plugin else null
        }
        .firstOrNull { it.canBuild(workspaceRoot) }
}
```

## 存在的问题

### 1. 性能问题

- **时间复杂度**：O(n)，每次查找都需要遍历所有已启动的插件
- **类型检查开销**：对每个插件都进行 `is BuildProvider` 检查
- **重复调用 `canBuild()`**：如果多个提供者都支持同一工作区类型，会多次调用检查方法

**影响**：虽然插件数量通常不多，但在频繁调用时（如按钮状态更新）可能成为性能瓶颈

### 2. 可扩展性问题

- **职责扩散**：如果未来需要其他能力提供者（如 `DeployProvider`、`TestProvider`、`LintProvider`），需要在 `PluginManager` 中为每种能力添加专门的查找方法
- **违反开闭原则**：每次新增能力类型都需要修改 `PluginManager`

### 3. 服务注册表未充分利用

- `PluginManager` 中已有 `servicesRegistry: MutableServiceRegistry`，但目前未被使用
- 系统中已有其他服务接口（`SearchService`、`ProjectService`、`DecompilerService`），但它们的使用方式不清楚

### 4. 多提供者冲突处理

- 当前使用 `firstOrNull`，如果多个插件都声称可以构建同一工作区，只会使用第一个匹配的
- 没有优先级机制或用户选择机制

## 改进方案

### 方案A：服务注册表模式（推荐）⭐

**核心思想**：插件在激活时向服务注册表注册 `BuildProvider`，查找时直接从注册表获取所有提供者。

**优点**：
- 性能更好：O(1) 注册，O(n) 查找（n 是 BuildProvider 数量而非所有插件）
- 职责清晰：PluginManager 不需要知道具体的提供者类型
- 易于扩展：新增能力类型只需定义接口，插件自行注册
- 符合现有架构：利用已有的 `ServiceRegistry`

**实现**：

```kotlin
// PluginManager 中添加
fun findBuildProviders(): List<BuildProvider> {
    return servicesRegistry.get(BuildProvider::class.java)
        ?.let { listOf(it) } // 如果注册表支持多实例，返回列表
        ?: emptyList()
}

fun findBuildProvider(workspaceRoot: File): BuildProvider? {
    return findBuildProviders()
        .firstOrNull { it.canBuild(workspaceRoot) }
}

// 插件激活时注册
override fun activate(context: PluginContext) {
    // ... 其他逻辑
    context.pluginManager().servicesRegistry.register(BuildProvider::class.java, this)
}

// 插件停用时取消注册
override fun deactivate() {
    context.pluginManager().servicesRegistry.unregister(BuildProvider::class.java)
}
```

**注意**：需要扩展 `ServiceRegistry` 支持同一类型的多个实例，或者使用 `Map<Class<*>, List<Any>>`

### 方案B：能力提供者注册表

**核心思想**：创建专门的能力注册表，按能力类型索引。

**优点**：
- 性能最优：O(1) 查找
- 支持多实例：一个插件可以提供多个能力
- 类型安全

**实现**：

```kotlin
class CapabilityRegistry {
    private val capabilities: MutableMap<Class<*>, MutableList<Any>> = mutableMapOf()
    
    fun <T : Any> register(capabilityClass: Class<T>, instance: T) {
        capabilities.getOrPut(capabilityClass) { mutableListOf() }.add(instance)
    }
    
    fun <T : Any> unregister(capabilityClass: Class<T>, instance: T) {
        capabilities[capabilityClass]?.remove(instance)
    }
    
    fun <T : Any> getAll(capabilityClass: Class<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return capabilities[capabilityClass]?.map { it as T } ?: emptyList()
    }
}
```

### 方案C：优化当前实现（渐进式改进）

**核心思想**：保持当前架构，但优化查找性能。

**改进点**：
1. 缓存 BuildProvider 列表
2. 在插件状态变化时更新缓存
3. 添加优先级支持

**实现**：

```kotlin
private var cachedBuildProviders: List<BuildProvider>? = null

fun findBuildProvider(workspaceRoot: File): BuildProvider? {
    val providers = cachedBuildProviders ?: run {
        val list = pluginsById.values
            .filter { it.state == PluginState.STARTED }
            .mapNotNull { if (it.plugin is BuildProvider) it.plugin as BuildProvider else null }
        cachedBuildProviders = list
        list
    }
    return providers.firstOrNull { it.canBuild(workspaceRoot) }
}

// 在插件状态变化时清除缓存
private fun firePluginStateChanged(pluginId: String) {
    cachedBuildProviders = null // 清除缓存
    pluginStateListeners.forEach { it.onPluginStateChanged(pluginId) }
}
```

### 方案D：扩展点模式（类似 Eclipse）

**核心思想**：使用扩展点机制，插件声明提供的能力。

**优点**：
- 高度解耦
- 支持声明式配置

**缺点**：
- 实现复杂
- 可能过度设计

## 推荐方案

### 首选：方案A（服务注册表模式）

**理由**：
1. **利用现有基础设施**：已有 `ServiceRegistry`，只需扩展支持多实例
2. **性能提升**：避免遍历所有插件
3. **易于扩展**：未来其他能力提供者可以使用相同模式
4. **职责清晰**：PluginManager 专注于插件生命周期管理

### 次选：方案C（渐进式优化）

**适用场景**：如果不想改变架构，只想快速优化性能

## 具体改进建议

### 1. 扩展 ServiceRegistry 支持多实例

```kotlin
class MutableServiceRegistry : ServiceRegistry {
    private val services: MutableMap<Class<*>, Any> = linkedMapOf()
    private val multiServices: MutableMap<Class<*>, MutableList<Any>> = mutableMapOf()
    
    // 单实例注册（向后兼容）
    fun <T : Any> register(serviceClass: Class<T>, instance: T) {
        services[serviceClass] = instance
    }
    
    // 多实例注册（新方法）
    fun <T : Any> registerMulti(serviceClass: Class<T>, instance: T) {
        multiServices.getOrPut(serviceClass) { mutableListOf() }.add(instance)
    }
    
    fun <T : Any> getAll(serviceClass: Class<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        return multiServices[serviceClass]?.map { it as T } ?: emptyList()
    }
}
```

### 2. 修改 PluginContext 提供注册能力

```kotlin
interface PluginContext {
    // ... 现有方法
    
    /**
     * 注册服务（多实例支持）
     */
    fun <T : Any> registerService(serviceClass: Class<T>, instance: T)
    
    /**
     * 取消注册服务
     */
    fun <T : Any> unregisterService(serviceClass: Class<T>, instance: T)
}
```

### 3. 重构 BuildProvider 查找

```kotlin
fun findBuildProvider(workspaceRoot: File): BuildProvider? {
    return servicesRegistry.getAll(BuildProvider::class.java)
        .firstOrNull { it.canBuild(workspaceRoot) }
}
```

### 4. 插件注册 BuildProvider

```kotlin
class AndroidPlugin : Plugin, BuildProvider {
    private var pluginContext: PluginContext? = null
    
    override fun activate(context: PluginContext) {
        pluginContext = context
        context.registerService(BuildProvider::class.java, this)
        // ... 其他逻辑
    }
    
    override fun deactivate() {
        pluginContext?.unregisterService(BuildProvider::class.java, this)
        pluginContext = null
        // ... 其他清理
    }
}
```

## 总结

当前 `BuildProvider` 架构设计**基本合理但可以优化**：

✅ **优点**：
- 接口设计清晰
- 使用简单
- 符合插件化思想

❌ **缺点**：
- 查找性能可优化
- 可扩展性不足
- 未充分利用服务注册表

📈 **推荐改进方向**：
使用服务注册表模式，让插件在激活时注册能力，查找时直接从注册表获取。这样既提升了性能，又为未来的能力扩展打下了良好基础。

