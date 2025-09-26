# EditorX

EditorX 是一个基于 Kotlin/JVM 的可扩展桌面编辑器，采用模块化与插件化架构，内置活动栏、侧边栏以及插件系统（源码插件与 JAR 插件）。插件体系参考了 PF4J 的思想，并结合本项目的 UI/交互做了精简实现。

## 构建与运行

- 启动 GUI：`./gradlew :gui:run`
- 构建所有模块：`./gradlew build`

运行后会自动发现并加载：
- 源码插件（同进程类路径）：使用 `ServiceLoader<editorx.plugin.Plugin>` 发现，并按包前缀 `editorx.` 过滤。
- JAR 插件（隔离类加载）：读取运行目录 `plugins/` 下的 JAR，优先使用 Manifest 的 `Main-Class` 作为插件主类；若缺失则回退扫描 JAR 内实现了 `Plugin` 的具体类。

## 主要特性

### 核心架构
- **模块化设计**：`core`、`gui`、`plugins` 模块分离
- **插件系统**：支持源码插件和JAR插件，自动类发现
- **事件总线（可选）**：core 提供事件总线实现，但默认 GUI 未注入使用

### 用户界面
- **ActivityBar**：左侧活动栏，包含Explorer等工具
- **SideBar**：侧边栏，显示活动栏选中的内容
- **Editor**：主编辑器区域，支持多标签页
- **StatusBar**：底部状态栏，显示文件信息
- **TitleBar**：顶部菜单栏

### 插件系统
- **ID 唯一**：以 `PluginInfo.id` 作为唯一标识进行索引与卸载。
- **发现与加载**：源码通过 `ServiceLoader`；JAR 在 `plugins/` 目录，Manifest-first，类扫描兜底。
- **生命周期与事件**：基础状态参考 PF4J（`CREATED/LOADED/STARTED/STOPPED/FAILED`）。如需要事件通知，可在创建 `PluginManager` 时注入事件总线以发布 `PluginLoaded/PluginUnloaded`。
- **资源隔离**：JAR 插件使用独立 `URLClassLoader`；源码插件复用应用类加载器。

### 编辑器功能
- **多标签页**：支持多个文件同时编辑
- **文件操作**：打开、保存、另存为
- **拖拽支持**：拖拽文件到编辑器打开
- **状态显示**：显示当前文件的行列信息

## 模块结构

```
EditorX
├── core/                    # 核心模块
│   ├── plugin/             # 插件API
│   ├── event/              # 事件总线（可选）
│   ├── settings/           # 设置管理
│   └── workspace/          # 工作区管理
├── gui/                    # GUI模块
│   ├── main/               # 主窗口和组件
│   │   ├── activitybar/    # 活动栏
│   │   ├── sidebar/        # 侧边栏
│   │   ├── editor/         # 编辑器
│   │   ├── titlebar/       # 标题栏
│   │   └── statusbar/      # 状态栏
│   ├── ui/                 # UI组件
│   └── services/           # GUI服务
└── plugins/                # 插件模块
    └── testplugin/         # 测试插件
```

## 插件开发

### 源码插件（ServiceLoader）
1) 编写插件类并实现 `editorx.plugin.Plugin`
```kotlin
class MyPlugin : Plugin {
    override fun getInfo(): PluginInfo {
        return PluginInfo(
            id = "my-plugin",
            name = "My Plugin",
            version = "1.0.0"
        )
    }
    
    override fun activate(context: PluginContext) {
        // 插件激活逻辑
    }
    
    override fun deactivate() {
        // 插件禁用逻辑
    }
}
```
2) 在插件模块的资源目录添加服务声明文件：`META-INF/services/editorx.plugin.Plugin`
   内容为实现类的完全限定名，例如：
```
editorx.plugins.myplugin.MyPlugin
```
3) 包名需以 `editorx.` 开头方会被加载器接受（用于限制加载范围）。

### JAR 插件（Manifest-first）
1) 实现 `Plugin` 接口并提供无参构造函数。
2) JAR 的 Manifest 建议设置 `Main-Class` 指向该实现类（如缺失将回退扫描，建议显式设置）：
```
Main-Class: editorx.plugins.explorer.ExplorerPlugin
```
3) 将 JAR 放入应用运行目录的 `plugins/` 文件夹。
4) 插件的名称、版本等元信息来自 `Plugin.getInfo()`，Manifest 中可保留其他元数据供将来扩展。

### UI 扩展（ActivityBar → SideBar）
在 `activate()` 中通过 `PluginContext.addActivityBarItem` 注册入口，提供一个 `ViewProvider` 或 `CachedViewProvider`：
```kotlin
context.addActivityBarItem(
    iconPath = "icons/my.svg",
    viewProvider = object : CachedViewProvider() {
        override fun createView(): JComponent = JPanel()
    }
)
```

## 技术栈

- **语言**：Kotlin 2.1.x
- **运行时**：JVM 21
- **构建工具**：Gradle (Kotlin DSL)
- **UI框架**：Swing
- **主题**：FlatLaf + Material3

## 开发规范

更多协作规范见 `AGENTS.md`。

## 注意事项
- 主类设置：`gui/build.gradle.kts` 中应用入口配置为 `editorx.gui.EditorGuiKt`。若运行失败，请确认包含 `main()` 的 Kotlin 文件名与入口类名一致（Kotlin 顶级函数生成的类名通常为 `文件名Kt`）。

## 许可证

本项目采用开源许可证，具体信息请查看LICENSE文件。
