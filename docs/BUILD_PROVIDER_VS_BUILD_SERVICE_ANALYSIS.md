# BuildProvider vs BuildService 命名和位置分析

## 当前状态

### BuildProvider（当前）
- **位置**：`editorx.core.plugin.BuildProvider`
- **命名**：Provider 后缀
- **注册方式**：通过 `PluginContext.registerService()` 注册到 `ServiceRegistry`
- **查找方式**：通过 `PluginManager.findBuildProvider()` 从 `ServiceRegistry` 查找

### DecompilerService（参考）
- **位置**：`editorx.core.service.DecompilerService`
- **命名**：Service 后缀
- **注册方式**：不清楚（代码中似乎未找到实际使用）
- **查找方式**：不清楚

## 对比分析

### 方案A：保持现状（BuildProvider in plugin 包）✅ 推荐

#### 优点

1. **与插件系统紧密耦合**
   - BuildProvider 是插件提供的能力，放在 `plugin` 包更符合其本质
   - 插件通过 `PluginContext` 注册，这是插件系统的一部分
   - 语义清晰：这是插件提供的"能力/提供者"

2. **命名更准确**
   - `Provider` 强调"提供能力"，适合多实例场景（多个插件都可以提供构建能力）
   - `Service` 通常暗示单例或唯一实例，但构建能力允许多个提供者（Android、Gradle、Maven 等）
   - 符合"提供者模式"（Provider Pattern）的设计思想

3. **职责边界清晰**
   - `plugin` 包：插件相关的能力接口（Plugin、PluginContext、PluginGuiProvider、BuildProvider）
   - `service` 包：核心服务基础设施（ServiceRegistry）
   - 分离清晰：服务注册表是基础设施，能力接口是插件契约

4. **符合现有架构模式**
   - `PluginGuiProvider` 也在 `plugin` 包中，命名一致
   - `FileHandler` 也在 `plugin` 包中，都是插件提供的能力
   - 保持了 `plugin` 包的完整性

5. **减少包依赖混乱**
   - 如果移到 `service` 包，`plugin` 包需要依赖 `service` 包来定义接口
   - 当前结构：`service` 包提供基础设施，`plugin` 包定义契约，依赖方向清晰

#### 缺点

1. **与 DecompilerService 命名不一致**
   - DecompilerService 使用 Service 后缀
   - 但 DecompilerService 似乎未被实际使用，可能是一个遗留或规划中的接口

2. **可能造成混淆**
   - 虽然注册到 ServiceRegistry，但接口不在 service 包中
   - 需要理解：ServiceRegistry 是基础设施，可以注册任何接口类型

### 方案B：移动到 service 包并改名为 BuildService

#### 优点

1. **命名统一**
   - 与 DecompilerService 保持一致
   - 符合"服务"的语义（提供构建服务）

2. **位置统一**
   - 所有服务接口都在 `service` 包中
   - 查找服务时逻辑更集中

3. **语义明确**
   - `Service` 后缀明确表示这是一个服务接口
   - 与 ServiceRegistry 的命名对应

#### 缺点

1. **语义不匹配**
   - `Service` 通常暗示单一实例，但构建能力支持多个提供者
   - `Provider` 更准确地表达了"提供者模式"，允许多实例

2. **职责边界模糊**
   - BuildService 是插件提供的能力，移到 service 包会模糊职责
   - service 包应该包含基础设施，而不是插件契约

3. **依赖方向混乱**
   - 如果 BuildService 在 service 包，但通过 PluginContext 注册，会形成循环依赖的错觉
   - `plugin` 包需要知道 service 包中的接口定义

4. **与现有模式不一致**
   - `PluginGuiProvider`、`FileHandler` 都在 `plugin` 包
   - 移动 BuildProvider 会打破这种一致性

5. **DecompilerService 参考价值有限**
   - DecompilerService 在代码库中似乎未被实际使用
   - 可能是一个未完成的设计或遗留代码
   - 不应该基于一个可能不正确的参考做决策

## 详细对比表

| 维度 | BuildProvider (plugin包) | BuildService (service包) |
|------|-------------------------|-------------------------|
| **语义准确性** | ✅ Provider 强调多实例提供 | ❌ Service 暗示单例 |
| **职责清晰度** | ✅ 插件能力在 plugin 包 | ❌ 插件能力在 service 包 |
| **命名一致性** | ⚠️ 与 DecompilerService 不一致 | ✅ 与 DecompilerService 一致 |
| **架构一致性** | ✅ 与 PluginGuiProvider、FileHandler 一致 | ❌ 与其他 Provider 不一致 |
| **依赖方向** | ✅ 清晰：service 提供基础设施 | ⚠️ 混乱：plugin 依赖 service |
| **使用场景** | ✅ 多实例（多个构建提供者） | ❌ 单实例暗示 |
| **代码组织** | ✅ plugin 包完整性 | ⚠️ service 包包含插件契约 |

## 参考其他系统

### Eclipse Plugin System
- 能力接口通常放在 `org.eclipse.*.core` 包
- 服务基础设施在单独的包
- 使用 `Provider` 后缀表示可扩展的能力

### VS Code Extension API
- 能力接口在 `vscode` 命名空间
- 使用 `Provider` 后缀（如 `TextDocumentContentProvider`、`FileSystemProvider`）
- 服务基础设施分离

### IntelliJ Platform
- 能力接口通常在功能相关包中
- 使用 `Provider` 后缀
- Service 通常指单例服务

## 推荐结论

### ✅ 推荐：保持现状（BuildProvider in plugin 包）

**理由**：

1. **语义准确性**：`Provider` 比 `Service` 更准确地表达多实例提供者的概念
2. **职责清晰**：插件能力接口应该放在 `plugin` 包，与 `PluginGuiProvider`、`FileHandler` 保持一致
3. **架构一致性**：符合现有的代码组织模式
4. **设计模式匹配**：符合提供者模式（Provider Pattern）的设计思想

### 关于 DecompilerService 的建议

如果 DecompilerService 确实应该被使用但当前未被使用，建议：
1. **保持 DecompilerService 在 service 包**（如果它是核心服务）
2. **或者也移到 plugin 包并改名为 DecompilerProvider**（如果它是插件提供的能力）
3. **统一使用 Provider 后缀**表示插件提供的能力接口

### 最终建议

**保持 BuildProvider 在 `editorx.core.plugin` 包，原因**：

1. ✅ 语义准确：Provider 强调多实例提供者
2. ✅ 职责清晰：插件能力接口在 plugin 包
3. ✅ 架构一致：与其他 Provider 接口保持一致
4. ✅ 设计合理：符合提供者模式

**关于命名约定**：
- `plugin` 包中的能力接口使用 `Provider` 后缀（如 `BuildProvider`、`PluginGuiProvider`）
- `service` 包中的服务使用 `Service` 后缀（如 `ServiceRegistry`）
- 如果未来有需要，可以统一将插件提供的能力接口命名为 `*Provider`，将核心服务命名为 `*Service`

