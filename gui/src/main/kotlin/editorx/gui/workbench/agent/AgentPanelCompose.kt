package editorx.gui.workbench.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import editorx.gui.ai.AiMessage
import editorx.gui.ai.AiRole
import editorx.gui.compose.toComposeColor
import editorx.gui.compose.toMaterialColors
import editorx.gui.theme.Theme
import editorx.gui.theme.ThemeManager
import javax.swing.SwingUtilities

/**
 * Agent 面板主组件 - 从上到下布局
 */
@Composable
fun AgentPanelCompose(mainWindow: MainWindow) {
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部工具栏
                Toolbar(theme = currentTheme)
                
                // 聊天记录区域
                ChatMessagesArea(
                    mainWindow = mainWindow,
                    theme = currentTheme,
                    modifier = Modifier.weight(1f)
                )
                
                // 底部输入框区域
                InputArea(mainWindow = mainWindow, theme = currentTheme)
            }
        }
    }
}

/**
 * 顶部工具栏
 */
@Composable
private fun Toolbar(theme: Theme) {
    val surfaceColor = theme.surface.toComposeColor()
    val textColor = theme.onSurface.toComposeColor()
    val mutedColor = theme.onSurfaceVariant.toComposeColor()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(surfaceColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧：应用名称和标签
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Lingma",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            // 标签（会话）
            Box(
                modifier = Modifier
                    .background(
                        theme.primary.toComposeColor().copy(alpha = 0.1f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "会话",
                    color = theme.primary.toComposeColor(),
                    fontSize = 12.sp
                )
            }
        }
        
        // 右侧：图标按钮和用户信息
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 图标按钮（暂时用文本表示，后续可替换为图标）
            IconButton("+", onClick = { }) // 新建
            IconButton("↻", onClick = { }) // 刷新
            IconButton("?", onClick = { }) // 帮助
            IconButton("✎", onClick = { }) // 编辑
            IconButton("⋮", onClick = { }) // 更多
            
            // 用户信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clickable { }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // 用户头像
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            Color(0xFF9C27B0), // 紫色背景
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LI",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "ixiaojye@163.com",
                    color = textColor,
                    fontSize = 12.sp
                )
                Text(
                    text = "▼",
                    color = mutedColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 图标按钮组件
 */
@Composable
private fun IconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Box(
        modifier = modifier
            .size(32.dp)
            .clickable(onClick = onClick)
            .hoverable(interactionSource)
            .background(
                if (isHovered) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
    }
}

/**
 * 聊天记录区域
 */
@Composable
private fun ChatMessagesArea(
    mainWindow: MainWindow,
    theme: Theme,
    modifier: Modifier = Modifier
) {
    // mainWindow 参数保留用于后续功能扩展
    // 示例消息数据
    val messages = remember {
        listOf(
            AiMessage(AiRole.USER, "hello"),
            AiMessage(
                AiRole.ASSISTANT,
                "你好!我是灵码(Lingma),由阿里云技术团队开发的AI编程助手。\n\n" +
                        "我可以帮助你：\n" +
                        "• 实时续写代码 (行级/函数级)\n" +
                        "• 用自然语言生成代码\n" +
                        "• 生成单元测试\n" +
                        "• 生成代码注释\n" +
                        "• 解释代码功能\n" +
                        "• 回答研发相关的智能问答\n" +
                        "• 排查异常报错等\n\n" +
                        "有什么我可以帮你的吗?"
            )
        )
    }
    
    val listState = rememberLazyListState()
    
    // 自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(theme.sidebarBackground.toComposeColor())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(messages) { message ->
            MessageBubble(message = message, theme = theme)
        }
    }
}

/**
 * 消息气泡组件
 */
@Composable
private fun MessageBubble(
    message: AiMessage,
    theme: Theme,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == AiRole.USER
    val backgroundColor = if (isUser) {
        theme.primary.toComposeColor()
    } else {
        theme.surface.toComposeColor()
    }
    val textColor = if (isUser) {
        theme.onPrimary.toComposeColor()
    } else {
        theme.onSurface.toComposeColor()
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI 头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        Color(0xFF9C27B0), // 紫色背景
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "灵",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Box(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
        
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        Color(0xFF9C27B0), // 紫色背景
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LI",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 底部输入框区域
 */
@Composable
private fun InputArea(
    mainWindow: MainWindow,
    theme: Theme
) {
    // mainWindow 参数保留用于后续功能扩展
    var inputText by remember { mutableStateOf("") }
    var agentExpanded by remember { mutableStateOf(false) }
    var autoExpanded by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    
    val surfaceColor = theme.surface.toComposeColor()
    val outlineColor = theme.outline.toComposeColor()
    val textColor = theme.onSurface.toComposeColor()
    val placeholderColor = theme.onSurfaceVariant.toComposeColor().copy(alpha = 0.6f)
    val mutedColor = theme.onSurfaceVariant.toComposeColor()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .padding(16.dp)
    ) {
        // 输入框面板（包含输入区域和按钮区域）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 200.dp)
                .background(surfaceColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .border(1.dp, outlineColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 输入文本区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    val textStyle = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )

                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = textStyle.copy(color = textColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .focusRequester(inputFocusRequester),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopStart
                            ) {
                                if (inputText.isBlank()) {
                                    Text(
                                        text = "输入消息...",
                                        color = placeholderColor,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                
                // 按钮区域（在输入框内部底部）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：Agent 和 Auto 下拉菜单
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Agent 下拉菜单
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { agentExpanded = true }
                                    .background(
                                        surfaceColor.copy(alpha = 0.6f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "∞",
                                    color = textColor,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Agent",
                                    color = textColor,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "▼",
                                    color = mutedColor,
                                    fontSize = 10.sp
                                )
                            }
                            
                            DropdownMenu(
                                expanded = agentExpanded,
                                onDismissRequest = { agentExpanded = false }
                            ) {
                                DropdownMenuItem(onClick = {
                                    agentExpanded = false
                                }) {
                                    Text("Agent 1")
                                }
                                DropdownMenuItem(onClick = {
                                    agentExpanded = false
                                }) {
                                    Text("Agent 2")
                                }
                            }
                        }
                        
                        // Auto 下拉菜单
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { autoExpanded = true }
                                    .background(
                                        surfaceColor.copy(alpha = 0.6f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Auto",
                                    color = textColor,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "▼",
                                    color = mutedColor,
                                    fontSize = 10.sp
                                )
                            }
                            
                            DropdownMenu(
                                expanded = autoExpanded,
                                onDismissRequest = { autoExpanded = false }
                            ) {
                                DropdownMenuItem(onClick = {
                                    autoExpanded = false
                                }) {
                                    Text("Auto")
                                }
                                DropdownMenuItem(onClick = {
                                    autoExpanded = false
                                }) {
                                    Text("Manual")
                                }
                            }
                        }
                    }
                    
                    // 右侧：图标按钮和发送按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // @ 按钮
                        IconButton(
                            icon = "@",
                            onClick = { },
                            modifier = Modifier.size(28.dp)
                        )
                        
                        // 地球图标按钮
                        IconButton(
                            icon = "🌐",
                            onClick = { },
                            modifier = Modifier.size(28.dp)
                        )
                        
                        // 图片图标按钮
                        IconButton(
                            icon = "🖼",
                            onClick = { },
                            modifier = Modifier.size(28.dp)
                        )
                        
                        // 发送按钮
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    // 发送消息逻辑
                                    if (inputText.isNotBlank()) {
                                        // TODO: 处理发送消息
                                        inputText = ""
                                    }
                                }
                                .background(
                                    theme.primary.toComposeColor(),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↑",
                                color = theme.onPrimary.toComposeColor(),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }
}
