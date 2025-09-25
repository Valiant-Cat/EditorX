# EditorX

EditorX 是一个基于 Kotlin/JVM 的可扩展桌面编辑器，采用模块化与插件化架构，内置命令面板、活动栏、侧边栏/底部面板容器以及插件系统（源码插件与 JAR 插件）。

## 构建与运行

- 启动 GUI：`./gradlew :gui:run`
- 构建所有模块：`./gradlew build`

运行后会自动扫描并加载：
- 源码插件：类路径下包前缀 `editorx` 实现 `editorx.plugin.Plugin` 的类
- JAR 插件：放入运行目录 `plugins/`，Manifest 配置 `Plugin-Id/Name/Version` 和 `Main-Class`

## 主要特性（本次升级要点）

- 插件 API 强化：插件可访问命令注册表、事件总线、设置存储与工作区
- 插件生命周期与事件：插件加载/卸载事件可在全局事件总线发布
- 命令系统：可通过“命令面板（Ctrl+Shift+P）”执行内置与插件命令
- 设置与工作区：基于 `~/.editorx/settings.properties` 的持久化配置与最近文件管理
- 编辑器增强：
  - 多标签 + 拖入文件打开
  - 修改痕迹标记（*）与保存/另存为
  - 状态栏行列显示与文件信息
- UI 容器契约：ActivityBar 控制视图，内容展示在 SideBar/Panel
- 插件管理对话框：查看已加载插件列表

## 模块

- `core`: 插件 API、事件总线、命令系统、设置与工作区接口
- `gui`: Swing GUI、命令面板、状态栏、活动栏/侧边栏/面板、插件管理器窗口
- `plugins/explorer`: 示例 JAR 插件（Explorer）
- `plugins/testplugin`: 示例源码插件（演示 ActivityBar 入口与命令注册）

更多协作规范见 `AGENTS.md`。
