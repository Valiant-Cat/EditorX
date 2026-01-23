package editorx.gui.workbench.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import java.awt.Cursor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
// 图标暂时使用文本替代，后续可以添加 Material Icons 依赖
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import editorx.gui.MainWindow
import editorx.gui.ai.AiConfigStore
import editorx.gui.ai.AiMessage
import editorx.gui.ai.AiModelConfig
import editorx.gui.ai.AiRole
import editorx.gui.ai.OpenAiResponsesClient
import editorx.gui.compose.toComposeColor
import editorx.gui.compose.toMaterialColors
import editorx.gui.theme.Theme
import editorx.gui.theme.ThemeManager
import java.awt.Color as AwtColor
import javax.swing.SwingUtilities

/**
 * Agent 代理数据模型
 */
data class AgentItem(
    val id: String,
    val name: String,
    val timeAgo: String
)

/**
 * 左侧面板 Compose 组件 - 聊天页面
 */
@Composable
fun LeftPanelCompose(mainWindow: MainWindow) {
    var currentTheme by remember { mutableStateOf(ThemeManager.currentTheme) }

    DisposableEffect(Unit) {
        val listener: () -> Unit = {
            SwingUtilities.invokeLater {
                currentTheme = ThemeManager.currentTheme
            }
        }
        ThemeManager.addThemeChangeListener(listener)
        onDispose { ThemeManager.removeThemeChangeListener(listener) }
    }

    val colors = remember(currentTheme) { currentTheme.toMaterialColors() }
    MaterialTheme(colors = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = currentTheme.sidebarBackground.toComposeColor()
        ) {
            ChatPanel(mainWindow, currentTheme)
        }
    }
}

/**
 * 右侧面板 Compose 组件 - 会话记录页面
 */
@Composable
fun RightPanelCompose(mainWindow: MainWindow) {
    var currentTheme by remember { mutableStateOf(ThemeManager.currentTheme) }

    DisposableEffect(Unit) {
        val listener: () -> Unit = {
            SwingUtilities.invokeLater {
                currentTheme = ThemeManager.currentTheme
            }
        }
        ThemeManager.addThemeChangeListener(listener)
        onDispose { ThemeManager.removeThemeChangeListener(listener) }
    }

    val colors = remember(currentTheme) { currentTheme.toMaterialColors() }
    MaterialTheme(colors = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = currentTheme.sidebarBackground.toComposeColor()
        ) {
            SessionsPanel(mainWindow, currentTheme)
        }
    }
}

/**
 * 聊天面板 - 左侧面板内容
 */
@Composable
private fun ChatPanel(mainWindow: MainWindow, theme: Theme) {
    var inputText by remember { mutableStateOf("") }
    val inputFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.sidebarBackground.toComposeColor())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部输入框
        InputArea(
            text = inputText,
            onTextChange = { inputText = it },
            placeholder = "Plan, @ for context, / for commands",
            theme = theme,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .focusRequester(inputFocusRequester)
        )

        // 消息列表区域（暂时为空，后续可以添加消息显示）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(theme.surface.toComposeColor(), RoundedCornerShape(8.dp))
        ) {
            // 这里可以添加消息列表
            Text(
                text = "消息列表区域",
                color = theme.onSurfaceVariant.toComposeColor(),
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }
}

/**
 * 会话记录面板 - 右侧面板内容
 */
@Composable
private fun SessionsPanel(mainWindow: MainWindow, theme: Theme) {
    var searchText by remember { mutableStateOf("") }
    val agents = remember {
        listOf(
            AgentItem("1", "Git 提交流程", "Now"),
            AgentItem("2", "编辑器架构插件化", "13d"),
            AgentItem("3", "Editor tab label offset", "13d")
        )
    }
    val filteredAgents = remember(searchText) {
        if (searchText.isBlank()) {
            agents
        } else {
            agents.filter { it.name.contains(searchText, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.sidebarBackground.toComposeColor())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 搜索框
        SearchBox(
            text = searchText,
            onTextChange = { searchText = it },
            placeholder = "Search Agents...",
            theme = theme,
            modifier = Modifier.fillMaxWidth()
        )

        // New Agent 按钮
        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF9C27B0), // 紫色
                contentColor = Color.White
            )
        ) {
            Text("New Agent")
        }

        // Sessions 标题
        Text(
            text = "Sessions",
            color = theme.onSurface.toComposeColor(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp)
        )

        // 代理列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredAgents) { agent ->
                AgentListItem(
                    agent = agent,
                    theme = theme,
                    onClick = { }
                )
            }
        }
    }
}

@Composable
private fun LeftPanel(
    modifier: Modifier,
    mainWindow: MainWindow,
    theme: Theme
) {
    val configStore = remember { AiConfigStore(mainWindow.guiContext.getAppDir()) }
    var inputText by remember { mutableStateOf("") }
    val inputFocusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .background(theme.sidebarBackground.toComposeColor())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部输入框
        InputArea(
            text = inputText,
            onTextChange = { inputText = it },
            placeholder = "Plan, @ for context, / for commands",
            theme = theme,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .focusRequester(inputFocusRequester)
        )

        // Local 部分
        LocalSection(
            theme = theme,
            modifier = Modifier.fillMaxWidth()
        )

        // 空白区域（填充剩余空间）
        Spacer(modifier = Modifier.weight(1f))
    }

    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }
}

@Composable
private fun InputArea(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    theme: Theme,
    modifier: Modifier = Modifier
) {
    val surfaceColor = theme.surface.toComposeColor()
    val outlineColor = theme.outline.toComposeColor()
    val textColor = theme.onSurface.toComposeColor()
    val placeholderColor = theme.onSurfaceVariant.toComposeColor().copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .background(surfaceColor, RoundedCornerShape(8.dp))
            .border(1.dp, outlineColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                color = textColor,
                fontSize = 13.sp
            ),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = placeholderColor,
                        fontSize = 13.sp
                    )
                }
                innerTextField()
            }
        )
    }
}


@Composable
private fun LocalSection(
    theme: Theme,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Local",
            color = theme.onSurface.toComposeColor(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun RightPanel(
    modifier: Modifier,
    theme: Theme
) {
    var searchText by remember { mutableStateOf("") }
    val agents = remember {
        listOf(
            AgentItem("1", "Git 提交流程", "Now"),
            AgentItem("2", "编辑器架构插件化", "13d"),
            AgentItem("3", "Editor tab label offset", "13d")
        )
    }
    val filteredAgents = remember(searchText) {
        if (searchText.isBlank()) {
            agents
        } else {
            agents.filter { it.name.contains(searchText, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .background(theme.sidebarBackground.toComposeColor())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 搜索框
        SearchBox(
            text = searchText,
            onTextChange = { searchText = it },
            placeholder = "Search Agents...",
            theme = theme,
            modifier = Modifier.fillMaxWidth()
        )

        // New Agent 按钮
        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = theme.primary.toComposeColor(),
                contentColor = theme.onPrimary.toComposeColor()
            ),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = "New Agent",
                fontSize = 13.sp
            )
        }

        // Sessions 标题
        Text(
            text = "Sessions",
            color = theme.onSurface.toComposeColor(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp, start = 0.dp)
        )

        // 代理列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredAgents) { agent ->
                AgentListItem(
                    agent = agent,
                    theme = theme,
                    onClick = { }
                )
            }
        }
    }
}

@Composable
private fun SearchBox(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    theme: Theme,
    modifier: Modifier = Modifier
) {
    val surfaceColor = theme.surface.toComposeColor()
    val outlineColor = theme.outline.toComposeColor()
    val textColor = theme.onSurface.toComposeColor()
    val placeholderColor = theme.onSurfaceVariant.toComposeColor().copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .background(surfaceColor, RoundedCornerShape(6.dp))
            .border(1.dp, outlineColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 12.sp
                ),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = placeholderColor,
                            fontSize = 12.sp
                        )
                    }
                    innerTextField()
                }
            )
    }
}

@Composable
private fun AgentListItem(
    agent: AgentItem,
    theme: Theme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val surfaceColor = theme.surface.toComposeColor()
    val textColor = theme.onSurface.toComposeColor()
    val mutedColor = theme.onSurfaceVariant.toComposeColor()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .hoverable(interactionSource)
            .background(
                if (isHovered) surfaceColor.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 0.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = agent.name,
                color = textColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = agent.timeAgo,
                color = mutedColor,
                fontSize = 11.sp
            )
        }
    }
}
