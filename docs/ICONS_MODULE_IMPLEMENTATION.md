# Icons 模块独立化实施总结

## 实施完成 ✅

已成功将图标资源独立为 `icons` 模块，采用混合模式（通用图标共享，GUI 专用图标集中管理）。

## 完成的工作

### 1. 创建 icons 模块
- ✅ 创建 `icons/build.gradle.kts`，依赖 `core` 模块
- ✅ 在 `settings.gradle.kts` 中添加 `include(":icons")`
- ✅ 创建目录结构：
  - `icons/src/main/resources/icons/common/` - 通用图标（15 个）
  - `icons/src/main/resources/icons/gui/` - GUI 专用图标（8 个）

### 2. 图标迁移
- ✅ 迁移 15 个通用图标到 `icons/common/`：
  - `folder.svg`, `search.svg`, `settings.svg`, `refresh.svg`, `locate.svg`
  - `collapseAll.svg`, `build.svg`, `addFile.svg`, `addDirectory.svg`
  - `anyType.svg`, `close.svg`, `arrow-down.svg`, `arrow-up.svg`
  - `chevron-down.svg`, `chevron-right.svg`
- ✅ 迁移 8 个 GUI 专用图标到 `icons/gui/`：
  - `layout-sidebar-left.svg`, `layout-sidebar-left-off.svg`
  - `resourcesRoot.svg`, `sourceRoot.svg`
  - `android-manifest.svg`, `application.svg`, `main-activity.svg`
  - `git-branch.svg`

### 3. IconLoader 增强
- ✅ 增强 `IconLoader.findResource()` 方法，支持多 ClassLoader 查找：
  1. 优先使用传入的 ClassLoader
  2. 尝试从 icons 模块的 ClassLoader 查找
  3. 使用 IconLoader 的 ClassLoader
  4. 最后使用系统 ClassLoader
- ✅ 添加 `findIconsModuleClassLoader()` 方法，自动发现 icons 模块的 ClassLoader

### 4. GUI 模块更新
- ✅ 在 `gui/build.gradle.kts` 中添加对 `icons` 模块的依赖
- ✅ 更新所有图标引用路径：
  - `ExplorerIcons.kt` - 更新 4 个图标路径
  - `ToolBar.kt` - 更新 9 个图标路径
  - `Explorer.kt` - 更新 3 个图标路径
  - `WelcomeView.kt` - 更新 4 个图标路径
  - `FindReplaceBar.kt` - 更新 5 个图标路径

### 5. 构建验证
- ✅ `icons` 模块构建成功
- ✅ `gui` 模块编译成功
- ✅ 所有图标文件正确迁移（共 23 个）

## 目录结构

```
icons/
  src/main/resources/icons/
    common/          # 通用图标（GUI 和插件共享）
      folder.svg
      search.svg
      settings.svg
      ...
    gui/             # GUI 专用图标
      layout-sidebar-left.svg
      resourcesRoot.svg
      ...
```

## 使用方式

### GUI 模块中使用
```kotlin
// 通用图标
IconLoader.getIcon(IconRef("icons/common/folder.svg"), 16)

// GUI 专用图标
IconLoader.getIcon(IconRef("icons/gui/resourcesRoot.svg"), 16)
```

### 插件模块中使用（未来）
插件可以添加对 `icons` 模块的依赖，然后使用通用图标：
```kotlin
// 在插件的 build.gradle.kts 中添加
dependencies {
    implementation(project(":icons"))
}

// 使用通用图标
IconLoader.getIcon(IconRef("icons/common/folder.svg"), 16)
```

## 优势

1. ✅ **统一管理**：所有图标集中在一个模块，便于维护
2. ✅ **资源共享**：GUI 和插件都可以使用通用图标
3. ✅ **避免重复**：解决了 `git-branch.svg` 重复的问题
4. ✅ **版本一致**：确保所有模块使用相同版本的图标
5. ✅ **向后兼容**：IconLoader 支持多 ClassLoader 查找，兼容现有代码

## 注意事项

1. **插件专用图标**：目前插件专用图标（如 `json.svg`, `xml.svg`）仍保留在各自模块，未来可以逐步迁移
2. **ClassLoader 兼容性**：IconLoader 已增强支持多 ClassLoader 查找，确保插件运行时能正确加载图标
3. **路径规范**：
   - 通用图标：`icons/common/xxx.svg`
   - GUI 专用：`icons/gui/xxx.svg`
   - 插件专用：保留在插件模块或未来迁移到 `icons/plugins/xxx.svg`

## 后续优化建议

1. 逐步将插件专用图标迁移到 `icons/plugins/` 目录
2. 建立图标命名和组织规范文档
3. 考虑添加图标预览工具或文档生成

