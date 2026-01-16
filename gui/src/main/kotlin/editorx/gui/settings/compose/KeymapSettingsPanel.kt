package editorx.gui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.gui.compose.toComposeColor
import editorx.gui.theme.Theme
import java.util.Locale
import javax.swing.KeyStroke

@Composable
fun KeymapSettingsPanel(
    theme: Theme,
    i18nVersion: Int,
) {
    val onSurface = theme.onSurface.toComposeColor()
    val onSurfaceMuted = theme.onSurfaceVariant.toComposeColor()
    val outline = theme.outline.toComposeColor()
    val surfaceVariant = theme.surfaceVariant.toComposeColor()

    val items = remember(i18nVersion) { buildKeymapItems() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = I18n.translate(I18nKeys.Settings.KEYMAP_TITLE),
            color = onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = I18n.translate(I18nKeys.Settings.KEYMAP_HINT),
            color = onSurfaceMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, outline, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = I18n.translate(I18nKeys.Keymap.ACTION),
                        color = onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.44f)
                    )
                    Text(
                        text = I18n.translate(I18nKeys.Keymap.SHORTCUT),
                        color = onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.22f)
                    )
                    Text(
                        text = I18n.translate(I18nKeys.Keymap.DESCRIPTION),
                        color = onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.34f)
                    )
                }
                Divider(color = outline)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    items.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.action,
                                color = onSurface,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.44f)
                            )
                            Text(
                                text = item.shortcut,
                                color = onSurface,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.22f)
                            )
                            Text(
                                text = item.description,
                                color = onSurfaceMuted,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.34f)
                            )
                        }
                        if (idx < items.lastIndex) {
                            Divider(color = outline.copy(alpha = 0.65f))
                        }
                    }
                }
            }
        }
    }
}

private data class KeymapItem(
    val action: String,
    val shortcut: String,
    val description: String,
)

private fun buildKeymapItems(): List<KeymapItem> {
    val items = mutableListOf<KeymapItem>()
    val registered = editorx.gui.shortcut.ShortcutManager.getAllShortcuts()
    registered.forEach { binding ->
        items.add(
            KeymapItem(
                action = binding.displayName,
                shortcut = formatKeyStroke(binding.keyStroke),
                description = binding.displayDescription
            )
        )
    }

    // 默认列表（避免遗漏 Editor 内部直接注册的快捷键）
    val shortcutMask = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    val defaultItems = listOf(
        KeymapItem(
            action = I18n.translate(I18nKeys.Action.FIND),
            shortcut = formatKeyStroke(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, shortcutMask)),
            description = I18n.translate(I18nKeys.Shortcut.FIND)
        ),
        KeymapItem(
            action = I18n.translate(I18nKeys.Action.REPLACE),
            shortcut = formatKeyStroke(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, shortcutMask)),
            description = I18n.translate(I18nKeys.Shortcut.REPLACE)
        ),
        KeymapItem(
            action = I18n.translate(I18nKeys.Action.SAVE),
            shortcut = formatKeyStroke(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, shortcutMask)),
            description = I18n.translate(I18nKeys.Shortcut.SAVE)
        ),
    )
    defaultItems.forEach { d ->
        if (items.none { it.shortcut == d.shortcut }) {
            items.add(d)
        }
    }
    return items.sortedBy { it.action }
}

private fun formatKeyStroke(keyStroke: KeyStroke): String {
    val keyCode = keyStroke.keyCode
    val modifiers = keyStroke.modifiers

    // 特殊：双击 Shift
    if (keyCode == java.awt.event.KeyEvent.VK_SHIFT && modifiers == 0) {
        return if (I18n.locale().language == Locale.ENGLISH.language) "Double Shift" else "双击Shift"
    }

    val modifiersEx = toExtendedModifiers(modifiers)
    val isMac = System.getProperty("os.name").lowercase().contains("mac")

    val parts = mutableListOf<String>()
    if ((modifiersEx and java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0) parts.add(if (isMac) "⇧" else "Shift")
    if ((modifiersEx and java.awt.event.InputEvent.ALT_DOWN_MASK) != 0) parts.add(if (isMac) "⌥" else "Alt")
    if ((modifiersEx and java.awt.event.InputEvent.META_DOWN_MASK) != 0) parts.add(if (isMac) "⌘" else "Meta")
    if ((modifiersEx and java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) parts.add(if (isMac) "⌃" else "Ctrl")

    val keyText = when (keyCode) {
        java.awt.event.KeyEvent.VK_COMMA -> ","
        java.awt.event.KeyEvent.VK_PERIOD -> "."
        java.awt.event.KeyEvent.VK_SLASH -> "/"
        java.awt.event.KeyEvent.VK_BACK_SLASH -> "\\"
        java.awt.event.KeyEvent.VK_OPEN_BRACKET -> "["
        java.awt.event.KeyEvent.VK_CLOSE_BRACKET -> "]"
        java.awt.event.KeyEvent.VK_SEMICOLON -> ";"
        java.awt.event.KeyEvent.VK_EQUALS -> "="
        java.awt.event.KeyEvent.VK_MINUS -> "-"
        java.awt.event.KeyEvent.VK_PLUS -> "+"
        else -> java.awt.event.KeyEvent.getKeyText(keyCode)
    }

    return if (parts.isEmpty()) keyText else {
        if (isMac) parts.joinToString("") + keyText else parts.joinToString("+") + "+" + keyText
    }
}

private fun toExtendedModifiers(modifiers: Int): Int {
    // 如果已经是扩展掩码，直接返回
    val hasExtended = (modifiers and (
        java.awt.event.InputEvent.SHIFT_DOWN_MASK or
            java.awt.event.InputEvent.CTRL_DOWN_MASK or
            java.awt.event.InputEvent.ALT_DOWN_MASK or
            java.awt.event.InputEvent.META_DOWN_MASK
        )) != 0
    if (hasExtended) return modifiers

    var ex = 0
    if ((modifiers and java.awt.event.InputEvent.SHIFT_MASK) != 0) ex = ex or java.awt.event.InputEvent.SHIFT_DOWN_MASK
    if ((modifiers and java.awt.event.InputEvent.CTRL_MASK) != 0) ex = ex or java.awt.event.InputEvent.CTRL_DOWN_MASK
    if ((modifiers and java.awt.event.InputEvent.ALT_MASK) != 0) ex = ex or java.awt.event.InputEvent.ALT_DOWN_MASK
    if ((modifiers and java.awt.event.InputEvent.META_MASK) != 0) ex = ex or java.awt.event.InputEvent.META_DOWN_MASK
    return ex
}

