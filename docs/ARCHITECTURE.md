# EditorX 架构与重构说明（草案）

> 目标：在不牺牲迭代速度的前提下，把 EditorX 从“功能堆叠型 Swing 工具”逐步演进为“核心能力可复用、平台实现可替换、插件可动态装卸”的可扩展产品。

## 0. 目标制定

### 0.1 核心设计原则（不变的基石）

- **主程序只是空壳容器**：仅负责生命周期管理与 UI 容器化，不直接承载反编译、解密、资源解析等业务能力，也不绑定 JADX、CFR、Apktool 等具体引擎。
- **一切能力皆为插件**：所有用户可见功能（含“打开 APK”）必须由插件提供；插件通过声明式 `activationEvents` 按需加载，避免无谓初始化。
- **零阻塞启动**：主线程只负责 UI 渲染，所有 I/O、类加载与初始化工作一律异步或延迟执行，确保冷启动无卡顿。

### 0.2 架构目标（可落地的技术规范）

1. **插件彻底解耦**：应用模块仅依赖 `plugin-api` 与 `common`，插件之间通过核心接口通信，不允许出现直接依赖；通过 Gradle 依赖图与 ArchUnit 规则持续校验。
2. **插件按需加载**：插件在首个激活事件触发时才装载对应 Class，加载后方可参与交互。
3. **热启/热停能力**：支持在不重启主程序的前提下禁用、启用或卸载插件，并及时释放资源。

### 0.3 用户体验目标（让“快”被感知）

1. **启动体验需具备“秒开”感**：窗口即刻出现并可交互，后台继续准备其余资源。
2. **文件加载无卡顿且有反馈**：打开文件时保证丝滑体验，同时提供清晰的进度或状态提示。

### 0.4 安全与可维护性

1. **崩溃隔离**：通过独立 `ClassLoader` 或后续沙箱机制，确保单个插件的异常不会拖累主程序。
2. **插件热安装策略**：支持部分插件即装即用，另一些在提示后待下次重启生效，策略由插件元数据声明。
3. **统一的插件清单（规划）**：后续可引入集中化的元数据清单以描述名称、版本、依赖和激活策略；当前由插件代码直接声明 `activationEvents`。

### 0.5 执行方案（落地路线）

| 步骤 | 目标要点 | 安排与交付物 | 验证方式 |
| --- | --- | --- | --- |
| Step-1 架构瘦身 | 主程序空壳、核心接口最小化 | - 抽取 `core-api`（纯 Kotlin）接口，只保留插件运行时与服务契约<br>- 把 GUI/工具链依赖迁移到 `platform-swing` 层或插件模块<br>- 建立 `ActivationEvent` 等按需加载机制 | 依赖图（Gradle + `./gradlew :core:dependencies`）无 Swing；ArchUnit 规则通过 |
| Step-2 插件运行时 | 插件按需加载、禁用/卸载 | - 插件通过代码声明 `activationEvents` 与 `restartPolicy`<br>- `PluginManager` 支持懒加载、状态持久化、禁用集<br>- 引入 `PluginLifecycleLogger` 统一日志 | 日志包含加载/激活时间戳；插件管理面板可启停且重启后记忆 |
| Step-3 功能插件化 | “一切能力皆插件” | - 将可替换能力（如 Settings、语言包、反编译器等）拆分为插件<br>- 插件提供默认 activationEvents（如 `onStartupFinished`, `onCommand:openFile`）<br>- 引入 `core-services`（ProjectService、DecompilerService 等）供插件调用 | 在主程序禁用对应插件后，功能消失且不会崩溃 |
| Step-4 UI/体验 | 顶部搜索、设置布局、国际化 | - 统一 `FindReplaceBar` 布局；提供命令式搜索/替换 API<br>- 设置窗口改为双栏 + 默认选中外观 + Reset/Cancel/Confirm 排列<br>- I18n 插件模式：按需加载语言包资源 | 手工走查 UI，命令键（⌘F/⌘R）可触发；语言切换持久化 |
| Step-5 引擎插件 | 集成 JADX + Smali | - 新建 `plugins:jadx`/`plugins:smali` 模块，实现 `DecompilerService` 和 `SmaliViewProvider`<br>- 支持 Smali/Java 双视图切换，按需激活 | 加载示例 APK，日志确认 ClassLoader 按需加载 |
| Step-6 性能与稳定 | 启动≤800ms、日志分级、崩溃隔离 | - 启动流程拆分：UI 线程只构建骨架，后台加载插件<br>- 引入 `StartupTimer`，记录阶段耗时并输出到日志<br>- 对插件 ClassLoader 提供 `close()`，禁用插件释放资源 | 自动化脚本测量冷启动；运行禁用/卸载后内存无增长 |

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
- **示例**：文件类型插件（XML/JSON/YAML/Smali）通过 `activationEvents` 约定在首次需要时激活；核心 Explorer/Search 作为基础组件保留在 GUI 模块中。

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
