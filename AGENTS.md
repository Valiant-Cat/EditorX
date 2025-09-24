# EditorX – 开发者与代理协作指南 (AGENTS)

本仓库是一个基于 Kotlin/JVM 的桌面应用，项目名为 EditorX，包含 GUI、核心 API 与插件示例。本文档为在本仓库内协作的「代码代理（Agents）」提供约定与操作指引。

## 项目概览

- 语言/运行时：Kotlin 2.1.x，JVM Toolchain 21
- 模块：
  - `core`：插件 API 与通用工具（包根：`editorx.*`）
  - `gui`：桌面应用 GUI（入口：`editorx.gui.EditorGuiKt`）
  - `plugins/explorer`：示例 JAR 插件（Manifest 指定主类）
  - `plugins/testplugin`：源码扫描的示例插件
- 构建工具：Gradle（Kotlin DSL）

## 构建与运行

- 运行 GUI 应用：`./gradlew :gui:run`
- 构建所有模块：`./gradlew build`
- Explorer 插件 JAR 构建由 `plugins/explorer/build.gradle.kts` 的 `afterEvaluate { tasks.jar { ... } }` 配置完成，打包后放入运行目录的 `plugins/` 以供加载。

## 包与命名

- 顶级包名统一为：`editorx`
- 重要命名空间：
  - GUI：`editorx.gui.*`
  - 插件 API：`editorx.plugin.*`
  - 工具类：`editorx.util.*`
- 应用入口类：`editorx.gui.EditorGuiKt`
- 插件源码扫描包前缀：`editorx`（见 `core/src/main/kotlin/editorx/plugin/PluginManager.kt`）

## 插件系统

- 两类插件：
  - 源码插件：放在源码内，类实现 `editorx.plugin.Plugin`，由 `PluginManager` 通过包前缀 `editorx` 扫描并加载。
  - JAR 插件：放入运行时的 `plugins/` 目录，通过 JAR Manifest 识别：
    - `Plugin-Name`、`Plugin-Version`、`Plugin-Description` (或 `Plugin-Desc`)
    - `Main-Class` 指向实现了 `Plugin` 接口的类，例如：`editorx.plugins.explorer.ExplorerPlugin`
- 插件上下文与交互：
  - 接口：`editorx.plugin.PluginContext`
  - 视图契约：`editorx.gui.ViewProvider` 与 `editorx.gui.ViewArea`
  - GUI 侧实现：`editorx.gui.plugin.GuiPluginContext`

## UI 布局约定（重要）

以下规则用于指导 UI 插件的放置与交互：

1. `SideBar` 与 `Panel` 都是容器。
2. `ActivityBar` 与 `TitleBar` 用于控制插件内容的打开/关闭。
3. 插件自行决定显示位置与控制按钮：
   - 可在 `ActivityBar` 注册入口按钮；
   - 点击后，其内容可显示在 `Panel` 或 `SideBar`（二者皆可）。

请在实现与评审时共同遵循以上统一约定。

## 目录结构（简）

- `core/src/main/kotlin/editorx/`：插件 API、工具类
- `gui/src/main/kotlin/editorx/`：应用入口与 GUI 组件
- `plugins/explorer/src/main/kotlin/editorx/plugins/explorer/ExplorerPlugin.kt`
- `plugins/testplugin/src/main/kotlin/editorx/plugins/testplugin/TestPlugin.kt`

## 对代理（Agents）的具体要求

- 修改或新增代码时务必使用 `editorx` 顶级包。
- 若涉及插件：
  - 源码插件应放入 `editorx.plugins.*` 命名空间；
  - JAR 插件需正确配置 Manifest 的 `Main-Class` 与元信息。
- 更新入口或清单时保持一致：`gui` 的 `mainClass` 必须为 `editorx.gui.EditorGuiKt`。
- 遵循模块边界：
  - `core` 不应依赖 GUI 具体实现；
  - `gui` 通过 `PluginContext` 等接口与插件交互。
- 变更涉及命名/路径时，同步更新构建脚本与引用。

如需扩展本文件，请保持条目简洁、面向执行与评审，避免与代码注释重复。
