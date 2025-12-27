# EditorX

[![Kotlin](https://img.shields.io/badge/kotlin-2.1.x-blue.svg)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-21-orange.svg)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)

EditorX 是一个基于 Kotlin/JVM 的可扩展桌面编辑器，采用模块化与插件化架构，支持源码插件和 JAR 插件的动态加载。插件系统参考了 PF4J 的设计思想，并结合本项目的 UI/交互做了精简实现。

## ✨ 主要特性

- 🔌 **插件化架构**：支持源码插件和 JAR 插件，支持动态加载、卸载和热插拔
- 🌍 **国际化支持**：基于插件的多语言系统，支持运行时切换语言
- 🎨 **现代化 UI**：基于 Swing + FlatLaf，提供 Material3 主题支持
- 📝 **多标签页编辑器**：支持多文件同时编辑，支持语法高亮和代码格式化
- 🗂️ **工作区管理**：支持工作区级别的文件管理和项目上下文
- 🔧 **服务注册机制**：插件可注册服务供其他组件使用（如构建服务、反编译服务等）

## 🚀 快速开始

### 环境要求

- JDK 21 或更高版本
- Gradle 8.0 或更高版本

### 构建与运行

```bash
# 克隆仓库
git clone <repository-url>
cd editorx

# 运行应用
./gradlew :gui:run

# 构建所有模块
./gradlew build

# 运行测试
./gradlew test
```

首次运行后，应用会在用户目录下创建 `.editorx` 配置目录。

### 打包发布

项目提供了面向最终用户的打包脚本（默认产物均输出到 `gui/build/distributions/`）：

```bash
# 通用 zip 分发包（bin/ lib/ plugins/）
./package.sh

# zip 分发包内置 Java 运行时（生成 gui-bundled-java.zip，下载即用）
./package.sh --bundle-java

# macOS: 额外生成 .app（以及可分发的 .app.zip）
./package.sh --mac-app
```

Windows（需在 Windows 环境运行，建议 PowerShell 7+）：

```powershell
pwsh -File package-windows.ps1 -Type app-image -Version 1.0.0
# 若需要 exe 安装包（可能需要 WiX Toolset）
pwsh -File package-windows.ps1 -Type exe -Version 1.0.0
```

### 插件安装

JAR 插件安装：
1. 将插件 JAR 文件放入应用运行目录的 `plugins/` 文件夹
2. 重启应用或通过设置界面重新扫描插件

## 📦 项目结构

```
EditorX/
├── core/                    # 核心模块
│   └── src/main/kotlin/editorx/core/
│       ├── plugin/         # 插件 API 和运行时
│       │   ├── loader/     # 插件加载器（SourcePluginLoader, JarPluginLoader, DuplexPluginLoader）
│       │   └── ...
│       ├── service/        # 服务注册表（ServiceRegistry）
│       ├── gui/            # GUI 扩展接口（GuiExtension）
│       ├── i18n/           # 国际化服务
│       ├── workspace/      # 工作区管理
│       └── util/           # 工具类
├── gui/                    # GUI 模块
│   └── src/main/kotlin/editorx/gui/
│       ├── workbench/      # 工作台组件
│       │   ├── activitybar/    # 活动栏
│       │   ├── sidebar/        # 侧边栏
│       │   ├── editor/         # 编辑器
│       │   ├── titlebar/       # 标题栏
│       │   ├── statusbar/      # 状态栏
│       │   ├── toolbar/        # 工具栏
│       │   ├── menubar/        # 菜单栏
│       │   └── navigationbar/  # 导航栏
│       ├── core/           # GUI 核心实现
│       │   ├── GuiExtensionImpl.kt
│       │   ├── GuiContextImpl.kt
│       │   └── ...
│       ├── settings/       # 设置相关
│       ├── search/         # 搜索相关
│       ├── shortcut/       # 快捷键管理
│       └── theme/          # 主题管理
├── icons/                  # 图标资源模块
│   └── src/main/resources/icons/
│       ├── common/         # 通用图标（GUI 和插件共享）
│       └── gui/            # GUI 专用图标
├── i18n-keys/              # 翻译键常量模块
│   └── src/main/kotlin/editorx/core/i18n/
└── plugins/                # 插件模块
    ├── android/            # Android 插件（APK 构建支持）
    ├── xml/                # XML 文件类型支持
    ├── json/               # JSON 文件类型支持
    ├── yaml/               # YAML 文件类型支持
    ├── smali/              # Smali 文件类型支持
    ├── git/                # Git 集成
    ├── i18n-zh/            # 中文语言包
    └── i18n-en/            # 英文语言包
```

## 🔌 插件开发

### 插件类型

EditorX 支持两种类型的插件：

1. **源码插件（SOURCE）**：随应用一起编译，通过 `ServiceLoader` 机制发现
2. **JAR 插件（JAR）**：独立的 JAR 文件，放置在 `plugins/` 目录，支持热插拔

### 创建源码插件

1. **实现 Plugin 接口**

```kotlin
package editorx.plugins.myplugin

import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginInfo
import editorx.core.plugin.PluginContext

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
        val gui = context.gui() ?: return
        
        // 注册文件类型
        gui.registerFileType(MyFileType())
        
        // 注册服务
        context.registerService(BuildService::class.java, MyBuildService())
    }
    
    override fun deactivate() {
        // 插件禁用逻辑，清理资源
    }
}
```

2. **添加服务声明文件**

在 `src/main/resources/META-INF/services/editorx.core.plugin.Plugin` 中添加：

```
editorx.plugins.myplugin.MyPlugin
```

3. **包名要求**

插件类的包名必须以 `editorx.` 开头，这是为了限制加载范围，确保只加载受信任的插件。

### 创建 JAR 插件

1. **实现 Plugin 接口**（同上）

2. **配置 JAR Manifest**（推荐）

在 `build.gradle.kts` 中配置：

```kotlin
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "editorx.plugins.myplugin.MyPlugin"
        )
    }
}
```

如果未设置 `Main-Class`，加载器会回退到扫描 JAR 内所有实现 `Plugin` 接口的类。

3. **打包并部署**

```bash
./gradlew :plugins:myplugin:jar
cp plugins/myplugin/build/libs/myplugin.jar plugins/
```

### 插件上下文（PluginContext）

`PluginContext` 提供插件与系统交互的接口：

- **GUI 扩展**：通过 `gui()` 获取 `GuiExtension`，可以注册文件类型、语法高亮、格式化器等
- **服务注册**：通过 `registerService()` 注册服务供其他组件使用
- **插件信息**：通过 `pluginId()` 和 `pluginInfo()` 获取插件标识和信息

### 服务注册

插件可以注册服务供其他组件使用：

```kotlin
override fun activate(context: PluginContext) {
    // 注册构建服务
    val buildService = MyBuildService()
    context.registerService(BuildService::class.java, buildService)
}

override fun deactivate() {
    // 取消注册服务（在 PluginManager 中自动处理）
}
```

### GUI 扩展

通过 `GuiExtension` 接口，插件可以：

- 注册文件类型（`registerFileType`）
- 注册语法高亮（`registerSyntaxHighlighter`）
- 注册格式化器（`registerFormatter`）
- 注册文件处理器（`registerFileHandler`）
- 添加工具栏按钮（`addToolBarItem`）
- 打开文件和工作区（`openFile`, `openWorkspace`）

在 `deactivate()` 中应该调用对应的 `unregisterAll*()` 方法清理资源。

### 插件生命周期

插件生命周期状态：
- `LOADED`：插件已加载但未激活
- `STARTED`：插件已激活
- `STOPPED`：插件已停止
- `FAILED`：插件激活失败

插件可以通过 `activationEvents()` 声明激活时机：
- `OnStartup`：应用启动时激活（默认）
- `OnCommand(commandId)`：特定命令触发时激活
- `OnDemand`：按需激活

## 🛠️ 技术栈

- **语言**：Kotlin 2.1.x
- **运行时**：JVM 21
- **构建工具**：Gradle (Kotlin DSL)
- **UI 框架**：Swing
- **主题**：FlatLaf + Material3
- **国际化**：基于 Java `Locale` 的插件化多语言系统
- **图标**：SVG 格式，统一管理在 `icons` 模块
- **日志**：SLF4J

## 📚 文档

- [架构文档](docs/ARCHITECTURE.md) - 详细的项目架构说明
- [开发指南](AGENTS.md) - 开发者协作指南（包含 AI 代理指南）

## 🤝 贡献指南

我们欢迎所有形式的贡献！

### 贡献流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request

### 代码规范

- 使用 Kotlin 官方编码规范
- 包名统一使用 `editorx` 作为顶级包
- 插件类包名必须以 `editorx.` 开头
- 遵循模块边界：`core` 不应依赖 `gui` 具体实现

### 提交信息规范

提交信息应清晰描述更改内容，建议格式：

```
<type>(<scope>): <subject>

<body>

<footer>
```

- `type`: feat, fix, docs, style, refactor, test, chore
- `scope`: 影响的模块或组件
- `subject`: 简短描述
- `body`: 详细说明（可选）
- `footer`: 相关 issue 或 breaking changes（可选）

## 📄 许可证

本项目采用 Apache 2.0 许可证，详见 [LICENSE](LICENSE) 文件。

## 🙏 致谢

- [PF4J](https://github.com/pf4j/pf4j) - 插件系统设计参考
- [FlatLaf](https://github.com/JFormDesigner/FlatLaf) - 现代化 Swing 外观
- [RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea) - 代码编辑器组件

---

如有问题或建议，欢迎开启 Issue 或 Pull Request！
