# EditorX 架构与重构说明（草案）

> 目标：在不牺牲迭代速度的前提下，把 EditorX 从“功能堆叠型 Swing 工具”逐步演进为“核心能力可复用、平台实现可替换、插件可动态装卸”的可扩展产品。

## 1. 当前现状与主要问题

- **核心逻辑与 UI/框架耦合**：`core` 目前直接依赖 Swing/FlatLaf/RSyntaxTextArea（例如 `FileType` 含 `Icon`、语法高亮注册直接操作 `TokenMakerFactory`），导致核心层无法在无 GUI 环境复用，也限制后续替换 UI 技术栈。
- **插件生命周期不完整**：现有插件仅支持启动时加载与激活，缺少卸载/禁用、资源回收与状态管理；JAR 插件 ClassLoader 未关闭，存在潜在内存泄漏风险。
- **注册表不可回收**：`FileTypeRegistry`/`SyntaxHighlighterRegistry` 仅支持注册不支持撤销，无法支撑插件动态卸载。
- **日志与错误处理不一致**：同时使用 `java.util.logging`、`println` 与 `slf4j`，难以统一定位问题与监控。

## 2. 目标架构（分层/边界）

建议以“核心（Core）/平台（Platform）/插件（Plugins）”三层为主线，逐步落地：

### 2.1 Core（纯业务/可复用）

- **仅包含**：模型、用例（UseCases）、服务接口、插件运行时（Plugin Runtime）、工具链抽象（Decompile/Build 等）。
- **不应依赖**：Swing、RSyntaxTextArea、FlatLaf 等 UI/平台细节（最终目标）。
- **对外暴露**：稳定的 API（如 `Plugin`、`PluginContext`、`DecompileService` 等）。

### 2.2 Platform（Swing 实现）

- **包含**：窗口、面板、渲染、快捷键、主题、编辑器控件适配（RSyntaxTextArea）。
- **负责**：把 Core 的抽象接口“落地”为 Swing 具体实现（例如语法高亮注册、ActivityBar/SideBar 的 UI 操作）。

### 2.3 Plugins（功能模块）

- **只依赖**：Core 的 API（必要时可依赖 Platform 的“契约接口”，但避免直接依赖 Swing 具体组件）。
- **贡献点**（建议）：视图入口、文件类型、语法高亮、工具链能力（如 JADX 反编译）、国际化资源等。
- **生命周期**：加载（Loaded）→ 启动（Started）→ 停止（Stopped）→ 卸载（Unloaded），并支持失败态（Failed）。

## 3. 本轮重构落地边界（可编译可运行优先）

为了控制风险，本轮优先落地“插件运行时 + 可卸载 + 管理 UI + 日志统一”，其余目标采用“抽象接口 + 骨架实现”逐步替换：

1) 插件系统：引入插件状态、动态启用/停用/卸载、JAR ClassLoader 生命周期管理；  
2) 注册表：为文件类型/语法高亮注册引入 ownerId，支持按插件卸载回收；  
3) UI：提供插件管理页面（列表/状态/启用停用/加载卸载/错误展示）；  
4) 日志：统一到 SLF4J（避免 `println` 与 `java.util.logging` 混用）；  
5) 反编译（JADX）：先抽象 `Decompiler` 接口与 GUI 入口，逐步集成实现；  
6) 国际化：先提供 i18n 基础设施与插件扩展点，再逐步把硬编码文案迁移为 key。

## 4. 推荐的后续演进路线（分阶段）

- **阶段 A（本轮）**：插件动态装卸 + 管理 UI + 日志统一 + 注册表可回收  
- **阶段 B**：抽离 `core` 对 Swing/RSyntaxTextArea 的依赖（引入 `platform-swing` 适配层）  
- **阶段 C**：集成 JADX 反编译（输出 Java 源码树 + 跳转/搜索），并与 Smali 视图联动  
- **阶段 D**：国际化插件（zh/en）+ 响应式布局与加载反馈统一（SwingWorker/进度条规范化）  

