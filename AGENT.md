# 布局结构概念

以下规则用于指导 UI 插件的放置与交互：

1. SideBar 和 Panel 都属于容器。
2. ActivityBar 和 TitleBar 用于控制插件内容的打开或关闭。
3. 插件决定自身显示位置与控制栏：插件可以显示在 ActivityBar 上；当点击该插件时，其内容可以显示在 Panel 或 SideBar 中，二者皆可。

以上为统一约定，请在实现与评审时共同遵循。
