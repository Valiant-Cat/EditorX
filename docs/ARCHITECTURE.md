# EditorX 架构设计文档

本文档详细说明 EditorX 的架构设计、核心组件和设计决策。

## 目录

- [架构概述](#架构概述)
- [核心设计原则](#核心设计原则)
- [模块架构](#模块架构)
- [插件系统](#插件系统)
- [服务注册机制](#服务注册机制)
- [GUI 扩展机制](#gui-扩展机制)
- [数据流](#数据流)
- [关键组件](#关键组件)

## 架构概述

EditorX 采用**分层模块化架构**，核心设计思想是**主程序作为容器，一切能力由插件提供**。

```
┌─────────────────────────────────────────┐
│           Application (GUI)             │
│  ┌──────────────┐  ┌─────────────────┐ │
│  │ PluginManager│  │  ServiceRegistry│ │
│  └──────────────┘  └─────────────────┘ │
└─────────────────────────────────────────┘
           │                    │
           │                    │
    ┌──────┴──────┐    ┌────────┴────────┐
    │   Plugins   │    │   Core API      │
    │             │    │                 │
    │ - Android   │    │ - Plugin        │
    │ - XML/JSON  │    │ - GuiExtension  │
    │ - I18n      │    │ - Service       │
    │ - ...       │    │ - Workspace     │
    └─────────────┘    └─────────────────┘
```

## 核心设计原则

### 1. 容器化设计

主程序（GUI）仅负责：
- UI 容器和生命周期管理
- 插件发现、加载和管理
- 服务注册表的维护
- 工作区和设置的持久化

业务能力（文件类型、语法高亮、构建、反编译等）均由插件提供。

### 2. 模块边界清晰

- **core 模块**：纯业务逻辑，不依赖 GUI 实现
- **gui 模块**：Swing 实现，依赖 core 模块
- **plugins 模块**：功能实现，只依赖 core 模块

### 3. 插件化一切

所有可扩展的能力都通过插件机制提供：
- 文件类型识别
- 语法高亮
- 代码格式化
- 构建能力
- 反编译能力
- 国际化资源

### 4. 服务注册机制

插件通过服务注册机制暴露能力，其他组件通过服务注册表查找和使用服务。

## 模块架构

### Core 模块

**职责**：定义核心 API 和插件运行时

**关键组件**：
- `Plugin` - 插件接口
- `PluginContext` - 插件上下文（类）
- `PluginManager` - 插件管理器
- `PluginLoader` - 插件加载器接口
- `ServiceRegistry` - 服务注册表
- `GuiExtension` - GUI 扩展接口
- `Workspace` - 工作区抽象
- `I18n` - 国际化服务

**依赖**：仅依赖 Kotlin 标准库和少量第三方库（如 SLF4J），不依赖 Swing。

### GUI 模块

**职责**：提供 Swing 实现的桌面应用

**关键组件**：
- `MainWindow` - 主窗口（位于 `gui/` 包）
- `Editor` - 代码编辑器（位于 `gui/workbench/editor/`）
- `SideBar` / `ActivityBar` - 侧边栏和活动栏（位于 `gui/workbench/sidebar/`, `gui/workbench/activitybar/`）
- `TitleBar` / `StatusBar` - 标题栏和状态栏（位于 `gui/workbench/titlebar/`, `gui/workbench/statusbar/`）
- `GuiExtensionImpl` - GUI 扩展实现（位于 `gui/core/`）
- `GuiContextImpl` - GUI 上下文实现（位于 `gui/core/`）

**目录结构**：
- `gui/workbench/` - 工作台组件（活动栏、侧边栏、编辑器、工具栏等）
- `gui/core/` - GUI 核心实现（扩展实现、上下文实现等）
- `gui/settings/` - 设置相关组件
- `gui/search/` - 搜索功能
- `gui/shortcut/` - 快捷键管理
- `gui/theme/` - 主题管理

**依赖**：依赖 core 模块，以及 Swing、FlatLaf、RSyntaxTextArea 等 UI 库。

### Plugins 模块

**职责**：提供各种功能插件

**示例插件**：
- `android` - Android APK 构建支持
- `xml`, `json`, `yaml`, `smali` - 文件类型支持
- `i18n-zh`, `i18n-en` - 国际化资源
- `git` - Git 集成

**依赖**：仅依赖 core 模块。

## 插件系统

### 插件加载机制

EditorX 实现了三种插件加载器：

1. **SourcePluginLoader**
   - 从 ClassPath 加载插件
   - 使用 `ServiceLoader<Plugin>` 发现插件
   - 插件来源标记为 `PluginOrigin.SOURCE`
   - 用于随应用一起编译的插件

2. **JarPluginLoader**
   - 从 `plugins/` 目录加载 JAR 文件
   - 每个 JAR 使用独立的 `URLClassLoader`
   - 插件来源标记为 `PluginOrigin.JAR`
   - 支持热插拔

3. **DuplexPluginLoader**
   - 组合 `SourcePluginLoader` 和 `JarPluginLoader`
   - 同时加载两种类型的插件
   - 默认使用的加载器

### 插件生命周期

```
发现 (Discovery)
  ↓
加载 (Loaded) ────┐
  ↓                │
激活事件触发      │
  ↓                │
启动 (Started) ←───┘ 失败 → (Failed)
  ↓
停止 (Stopped)
  ↓
卸载 (Unloaded)
```

**状态说明**：
- `LOADED`：插件已加载到内存，但尚未激活
- `STARTED`：插件已激活，`activate()` 已调用
- `STOPPED`：插件已停止，`deactivate()` 已调用
- `FAILED`：插件激活或运行时出错

### 插件激活策略

插件通过 `activationEvents()` 声明激活时机：

- `OnStartup`：应用启动时自动激活（默认）
- `OnCommand(commandId)`：特定命令触发时激活
- `OnDemand`：按需激活

### 插件上下文（PluginContext）

`PluginContext` 是类（不是接口），每个插件实例拥有独立的上下文。

**提供的功能**：
- `gui(): GuiExtension?` - 获取 GUI 扩展接口
- `registerService(serviceClass, instance)` - 注册服务
- `unregisterService(serviceClass, instance)` - 取消注册服务
- `pluginId()`, `pluginInfo()` - 获取插件标识和信息
- `active()`, `deactivate()`, `isActive()` - 生命周期管理

### 资源管理

插件在激活时可以注册资源（文件类型、语法高亮等），在停用时必须清理：

```kotlin
override fun activate(context: PluginContext) {
    val gui = context.gui() ?: return
    gui.registerFileType(MyFileType())
    gui.registerSyntaxHighlighter(Language.XML, MyHighlighter())
}

override fun deactivate() {
    val gui = pluginContext?.gui()
    gui?.unregisterAllFileTypes()
    gui?.unregisterAllSyntaxHighlighters()
}
```

系统通过 `ownerId`（插件 ID）跟踪资源归属，确保卸载插件时资源被正确清理。

## 服务注册机制

### ServiceRegistry

`ServiceRegistry` 是核心服务注册表，支持**多实例注册**（同一服务类型可以注册多个实例）。

**接口定义**：
```kotlin
interface ServiceRegistry {
    fun <T : Any> getService(serviceClass: Class<T>): List<T>
    fun <T : Any> registerService(serviceClass: Class<T>, instance: T)
    fun <T : Any> unregisterService(serviceClass: Class<T>, instance: T)
}
```

### 服务注册流程

1. **插件注册服务**：
   ```kotlin
   override fun activate(context: PluginContext) {
       val buildService = MyBuildService()
       context.registerService(BuildService::class.java, buildService)
   }
   ```

2. **其他组件查找服务**：
   ```kotlin
   val buildServices = serviceRegistry.getService(BuildService::class.java)
   val provider = buildServices.firstOrNull { it.canBuild(workspaceRoot) }
   ```

3. **插件卸载时自动清理**：
   系统在插件 `deactivate()` 时自动取消注册该插件的所有服务。

### 服务示例

- **BuildService**：提供构建能力（如 Android APK 构建）
- **DecompilerService**：提供反编译能力（规划中）

## GUI 扩展机制

### GuiExtension 接口

`GuiExtension` 接口位于 `editorx.core.gui` 包，定义了插件与 GUI 系统交互的契约。

**主要能力**：
1. **文件类型注册**：`registerFileType`, `unregisterAllFileTypes`
2. **语法高亮注册**：`registerSyntaxHighlighter`, `unregisterAllSyntaxHighlighters`
3. **格式化器注册**：`registerFormatter`, `unregisterAllFormatters`
4. **文件处理器注册**：`registerFileHandler`, `unregisterAllFileHandlers`
5. **工具栏管理**：`addToolBarItem`, `setToolBarItemEnabled`
6. **工作区操作**：`openWorkspace`, `openFile`
7. **进度显示**：`showProgress`, `hideProgress`
8. **主题颜色**：`getThemeTextColor`, `getThemeDisabledTextColor`
9. **编辑器右键菜单项**：`registerEditorContextMenuItem`, `unregisterAllEditorContextMenuItems`

### GuiExtensionImpl

`GuiExtensionImpl` 是 `GuiExtension` 的 Swing 实现，位于 `editorx.gui.core` 包。

**实现细节**：
- 通过 `ownerId`（插件 ID）跟踪资源归属
- 委托给 GUI 模块的注册表（`FileTypeRegistry`, `SyntaxHighlighterRegistry` 等）
- 确保资源按插件隔离，支持独立卸载

### 资源注册表

GUI 模块维护多个资源注册表：
- `FileTypeRegistry` - 文件类型注册表
- `SyntaxHighlighterRegistry` - 语法高亮注册表
- `FormatterRegistry` - 格式化器注册表
- `FileHandlerRegistry` - 文件处理器注册表
- `EditorContextMenuManager` - 编辑器右键菜单项注册表

每个注册表都支持按 `ownerId` 批量卸载资源。

## 数据流

### 应用启动流程

```
1. GuiApp.main()
   ↓
2. 初始化 GUI（主题、外观等）
   ↓
3. 创建 PluginManager
   ↓
4. 使用 DuplexPluginLoader 扫描插件
   ├─ SourcePluginLoader 加载源码插件
   └─ JarPluginLoader 加载 JAR 插件
   ↓
5. 加载插件到内存（Loaded 状态）
   ↓
6. 触发 OnStartup 事件，激活插件（Started 状态）
   ↓
7. 显示主窗口，应用就绪
```

### 插件激活流程

```
1. PluginManager.triggerStartup()
   ↓
2. 查找所有 OnStartup 插件
   ↓
3. 对每个插件调用 startPlugin(pluginId)
   ↓
4. PluginContext.active()
   ↓
5. Plugin.activate(context)
   ├─ 注册文件类型
   ├─ 注册语法高亮
   ├─ 注册服务
   └─ 其他初始化
   ↓
6. 状态更新为 STARTED
```

### 服务查找流程

```
1. 组件需要某个服务（如 BuildService）
   ↓
2. 调用 ServiceRegistry.getService(BuildService::class.java)
   ↓
3. 返回所有已注册的 BuildService 实例列表
   ↓
4. 组件根据业务逻辑筛选合适的服务
   ↓
5. 调用服务方法
```

## 关键组件

### PluginManager

**职责**：
- 插件发现和加载
- 插件生命周期管理
- 插件状态跟踪
- 插件启用/禁用管理
- ClassLoader 生命周期管理（JAR 插件）

**关键方法**：
- `scanPlugins(loader: PluginLoader)` - 扫描并加载插件
- `startPlugin(pluginId: String)` - 启动插件
- `stopPlugin(pluginId: String)` - 停止插件
- `unloadPlugin(pluginId: String)` - 卸载插件
- `findBuildService(workspaceRoot: File)` - 查找构建服务

### PluginContext

**职责**：
- 提供插件与系统交互的接口
- 管理插件状态
- 委托服务注册到 ServiceRegistry
- 提供 GUI 扩展访问

**设计决策**：
- 设计为类而非接口，简化实现
- 每个插件实例拥有独立的上下文
- 通过构造函数注入 `ServiceRegistry`，避免全局状态

### ServiceRegistry

**职责**：
- 服务实例的注册和查找
- 支持多实例注册（同一服务类型可以有多个实现）

**设计决策**：
- 使用 `Map<Class<*>, List<Any>>` 存储多实例
- 接口和实现分离，便于测试和扩展

### PluginLoader 层次结构

```
PluginLoader (接口)
  ├─ SourcePluginLoader (源码插件加载)
  ├─ JarPluginLoader (JAR 插件加载)
  └─ DuplexPluginLoader (组合加载器)
       ├─ SourcePluginLoader
       └─ JarPluginLoader
```

**设计优势**：
- 单一职责：每个加载器只负责一种插件类型
- 易于扩展：可以添加新的加载器而不影响现有代码
- 灵活组合：可以根据需要组合不同的加载器

## 设计模式应用

### 1. 插件模式（Plugin Pattern）

通过 `Plugin` 接口定义插件契约，实现功能的模块化和可扩展性。

### 2. 注册表模式（Registry Pattern）

使用 `ServiceRegistry` 和服务注册机制，实现服务的发现和依赖注入。

### 3. 策略模式（Strategy Pattern）

`PluginLoader` 接口及其实现（`SourcePluginLoader`, `JarPluginLoader`）体现了策略模式。

### 4. 外观模式（Facade Pattern）

`GuiExtension` 接口作为 GUI 系统的外观，隐藏内部复杂性，提供简洁的 API。

### 5. 组合模式（Composite Pattern）

`DuplexPluginLoader` 组合多个加载器，统一接口。

## 未来演进方向

### 短期目标

1. **完善服务机制**：添加更多服务类型（如 `DecompilerService`, `TestService`）
2. **插件依赖管理**：支持插件间依赖声明和解析
3. **插件配置系统**：支持插件配置文件和管理界面

### 中期目标

1. **性能优化**：插件懒加载优化，启动性能提升
2. **错误处理**：改进插件错误的隔离和恢复机制
3. **插件沙箱**：增强插件安全隔离（如果需要）

### 长期目标

1. **跨平台支持**：考虑非 Swing 的 UI 实现（如 Compose Multiplatform）
2. **插件市场**：插件发现、安装和管理的一体化解决方案
3. **API 稳定性**：建立稳定的插件 API 版本管理机制

---

**文档维护**：本文档应随架构变更及时更新。重大架构决策应在此文档中记录。
