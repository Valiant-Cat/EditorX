package editorx.gui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * Compose Desktop 示例入口（不影响现有 Swing 主入口）。
 *
 * 运行方式：
 * - `./gradlew :gui:runComposeDemo`
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "EditorX Compose Demo"
    ) {
        MaterialTheme {
            var counter by remember { mutableStateOf(0) }
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("EditorX Compose 已启用（可在 Swing 中嵌入）")
                Text("计数：$counter")
                Button(onClick = { counter += 1 }) {
                    Text("点击 +1")
                }
            }
        }
    }
}

