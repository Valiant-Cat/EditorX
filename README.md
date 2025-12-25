# EditorX

EditorX 是一个基于 Kotlin/JVM 的可扩展桌面编辑器，采用模块化与插件化架构，内置活动栏、侧边栏以及插件系统（源码插件与 JAR 插件）。插件体系参考了 PF4J 的思想，并结合本项目的 UI/交互做了精简实现。

## 构建与运行

- 启动 GUI：`./gradlew :gui:run`
- 构建所有模块：`./gradlew build`

运行后会自动发现并加载：
- 源码插件（同进程类路径）：使用 `ServiceLoader<editorx.core.plugin.Plugin>` 发现，并按包前缀 `editorx.` 过滤。
- JAR 插件（隔离类加载）：读取运行目录 `plugins/` 下的 JAR，优先使用 Manifest 的 `Main-Class` 作为插件主类；若缺失则回退扫描 JAR 内实现了 `Plugin` 的具体类。

## 主要特性

### 核心架构
- **模块化设计**：`core`、`gui`、`icons`、`i18n-keys`、`plugins` 模块分离
- **插件系统**：支持源码插件和JAR插件，自动类发现
- **事件总线（可选）**：core 提供事件总线实现，但默认 GUI 未注入使用
- **国际化支持**：基于插件的多语言系统，支持运行时切换语言
- **图标系统**：统一的图标资源管理，支持 GUI 和插件共享通用图标

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

### 国际化系统
- **插件化语言包**：每个语言包作为独立插件，一个插件对应一种语言
- **实时翻译**：翻译结果实时从提供器获取，支持动态更新
- **智能回退**：支持语言回退机制（如 `zh_TW` → `zh` → `zh_CN`）
- **翻译键常量**：所有翻译键集中在 `i18n-keys` 模块，提供类型安全的常量类
- **动态语言选择**：设置界面根据已注册的语言包动态显示语言选项

### 图标系统
- **统一管理**：所有图标资源集中在 `icons` 模块，便于维护和版本控制
- **分类组织**：通用图标（`common/`）和 GUI 专用图标（`gui/`）分类存放
- **资源共享**：GUI 和插件都可以使用通用图标，避免重复资源
- **智能加载**：`IconLoader` 支持多 ClassLoader 查找，自动发现 icons 模块资源
- **SVG 支持**：支持 SVG 格式图标，可无损缩放

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
│   ├── i18n/               # 国际化服务
│   ├── settings/           # 设置管理
│   └── workspace/          # 工作区管理
├── icons/                   # 图标资源模块
│   └── resources/icons/
│       ├── common/         # 通用图标（GUI和插件共享）
│       └── gui/            # GUI专用图标
├── i18n-keys/              # 翻译键常量模块
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
    ├── i18n-zh/            # 中文语言包
    ├── i18n-en/            # 英文语言包
    └── ...                 # 其他功能插件
```

## 插件开发

### 源码插件（ServiceLoader）
1) 编写插件类并实现 `editorx.core.plugin.Plugin`
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
2) 在插件模块的资源目录添加服务声明文件：`META-INF/services/editorx.core.plugin.Plugin`
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

### 语言包插件开发
语言包插件用于提供多语言支持，每个语言包插件对应一种语言。

1) 继承 `I18nPlugin` 基类：
```kotlin
import editorx.core.i18n.I18nPlugin
import editorx.core.i18n.I18nKeys
import editorx.core.plugin.PluginInfo
import java.util.Locale

class MyLanguagePlugin : I18nPlugin(Locale("ja")) {  // 例如：日语
    
    override fun getInfo() = PluginInfo(
        id = "i18n-ja",
        name = "Japanese (i18n)",
        version = "0.0.1"
    )
    
    override fun translate(key: String): String? {
        // 返回 key 对应的翻译，如果不存在则返回 null
        return dictionary[key]
    }
    
    override fun getAllKeys(): List<String> {
        // 可选：返回该语言包支持的所有 key 列表
        // 用于帮助开发者了解需要翻译哪些 key
        return dictionary.keys.toList()
    }
    
    private val dictionary = mapOf(
        I18nKeys.Menu.FILE to "ファイル",
        I18nKeys.Menu.EDIT to "編集",
        // ... 更多翻译
    )
}
```

2) 在资源目录添加服务声明：`META-INF/services/editorx.core.plugin.Plugin`
   内容为插件类的完全限定名。

3) 在 `build.gradle.kts` 中添加依赖：
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":i18n-keys"))  // 用于访问 I18nKeys 常量
}
```

4) 翻译键使用 `I18nKeys` 常量，避免硬编码字符串。

5) 语言名称翻译：使用 `lang.{locale}` 格式的 key（如 `lang.ja`），如果语言包未提供，系统会自动使用 `Locale.getDisplayName()` 作为回退。

### 图标使用
插件可以使用 `icons` 模块中的通用图标，避免重复资源。

1) 在 `build.gradle.kts` 中添加依赖：
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":icons"))  // 用于访问通用图标
}
```

2) 使用 `IconLoader` 加载图标：
```kotlin
import editorx.core.util.IconLoader
import editorx.core.util.IconRef

// 使用通用图标
val folderIcon = IconLoader.getIcon(IconRef("icons/common/folder.svg"), 16)

// 使用 GUI 专用图标（需要 GUI 模块依赖）
val sidebarIcon = IconLoader.getIcon(IconRef("icons/gui/layout-sidebar-left.svg"), 16)
```

3) 图标路径规范：
   - 通用图标：`icons/common/xxx.svg`（推荐插件使用）
   - GUI 专用：`icons/gui/xxx.svg`（GUI 模块专用）
   - 插件专用：可保留在插件模块中，或未来迁移到 `icons/plugins/xxx.svg`

4) `IconLoader` 会自动从多个 ClassLoader 查找图标资源，包括 icons 模块的 ClassLoader。

### UI 扩展
注意：插件不再支持在 SideBar 中注册视图。SideBar 固定显示 Explorer。

## 技术栈

- **语言**：Kotlin 2.1.x
- **运行时**：JVM 21
- **构建工具**：Gradle (Kotlin DSL)
- **UI框架**：Swing
- **主题**：FlatLaf + Material3
- **国际化**：基于 Java `Locale` 的插件化多语言系统
- **图标**：SVG 格式，统一管理在 `icons` 模块

## 开发规范

更多协作规范见 `AGENTS.md`。

## 注意事项
- 主类设置：`gui/build.gradle.kts` 中应用入口配置为 `editorx.gui.EditorGuiKt`。若运行失败，请确认包含 `main()` 的 Kotlin 文件名与入口类名一致（Kotlin 顶级函数生成的类名通常为 `文件名Kt`）。

## 许可证

本项目采用开源许可证，具体信息请查看LICENSE文件。
