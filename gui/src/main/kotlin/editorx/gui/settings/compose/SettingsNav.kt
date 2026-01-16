package editorx.gui.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.gui.settings.SettingsDialog

@Composable
fun SettingsNav(
    selectedSection: SettingsDialog.Section,
    hasAppearanceChanges: Boolean,
    outline: androidx.compose.ui.graphics.Color,
    surface: androidx.compose.ui.graphics.Color,
    primary: androidx.compose.ui.graphics.Color,
    onPrimary: androidx.compose.ui.graphics.Color,
    onSurface: androidx.compose.ui.graphics.Color,
    onSurfaceMuted: androidx.compose.ui.graphics.Color,
    onSelect: (SettingsDialog.Section) -> Unit,
) {
    val items = remember {
        listOf(
            SettingsDialog.Section.APPEARANCE to I18nKeys.Settings.APPEARANCE,
            SettingsDialog.Section.KEYMAP to I18nKeys.Settings.KEYMAP,
            SettingsDialog.Section.PLUGINS to I18nKeys.Settings.PLUGINS,
            SettingsDialog.Section.CACHE to I18nKeys.Settings.CACHE,
        )
    }

    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(surface)
            .border(width = 1.dp, color = outline)
            .padding(12.dp),
    ) {
        Text(
            text = I18n.translate(I18nKeys.Settings.PREFERENCES),
            color = onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Divider(color = outline.copy(alpha = 0.6f))
        Spacer(Modifier.height(8.dp))

        items.forEach { (section, key) ->
            SettingsNavItem(
                title = I18n.translate(key),
                selected = section == selectedSection,
                hasChanges = section == SettingsDialog.Section.APPEARANCE && hasAppearanceChanges,
                surface = surface,
                primary = primary,
                onPrimary = onPrimary,
                onSurface = onSurface,
                onSurfaceMuted = onSurfaceMuted,
                onClick = { onSelect(section) }
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun SettingsNavItem(
    title: String,
    selected: Boolean,
    hasChanges: Boolean,
    surface: androidx.compose.ui.graphics.Color,
    primary: androidx.compose.ui.graphics.Color,
    onPrimary: androidx.compose.ui.graphics.Color,
    onSurface: androidx.compose.ui.graphics.Color,
    onSurfaceMuted: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val bg = if (selected) primary else surface
    val fg = when {
        selected -> onPrimary
        hasChanges -> primary
        else -> onSurface
    }
    val weight = if (selected || hasChanges) FontWeight.Medium else FontWeight.Normal

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = fg,
                fontSize = 13.sp,
                fontWeight = weight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (hasChanges && !selected) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(onSurfaceMuted.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
