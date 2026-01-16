package editorx.gui.settings.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
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
import editorx.gui.compose.toComposeColor
import editorx.gui.theme.Theme
import editorx.gui.theme.ThemeManager
import java.util.Locale

@Composable
fun AppearanceSettingsPanel(
    theme: Theme,
    i18nVersion: Int,
    localeToShow: Locale,
    hasPendingLocale: Boolean,
    onSelectLocale: (Locale) -> Unit,
    onRevertLocale: () -> Unit,
    onSelectTheme: (Theme) -> Unit,
) {
    val availableLocales = remember(i18nVersion) { I18n.getAvailableLocales() }
    val onSurface = theme.onSurface.toComposeColor()
    val onSurfaceMuted = theme.onSurfaceVariant.toComposeColor()
    val outline = theme.outline.toComposeColor()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = I18n.translate(I18nKeys.Settings.APPEARANCE),
                color = onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (hasPendingLocale) {
                OutlinedButton(onClick = onRevertLocale) {
                    Text(I18n.translate(I18nKeys.Action.REVERT_CHANGES))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = I18n.translate(I18nKeys.Settings.APPEARANCE_TIP),
            color = onSurfaceMuted,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(16.dp))
        SectionCard(
            title = I18n.translate(I18nKeys.Settings.LANGUAGE),
            outline = outline,
            titleColor = onSurface
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableLocales.forEach { locale ->
                    val selected = locale == localeToShow
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLocale(locale) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { onSelectLocale(locale) })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = languageDisplayName(locale),
                            color = onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionCard(
            title = I18n.translate(I18nKeys.Settings.THEME),
            outline = outline,
            titleColor = onSurface
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val isLight = ThemeManager.currentTheme is Theme.Light
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTheme(Theme.Light) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isLight, onClick = { onSelectTheme(Theme.Light) })
                    Spacer(Modifier.width(8.dp))
                    Text(I18n.translate(I18nKeys.Theme.LIGHT), color = onSurface, fontSize = 13.sp)
                }

                val isDark = ThemeManager.currentTheme is Theme.Dark
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTheme(Theme.Dark) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isDark, onClick = { onSelectTheme(Theme.Dark) })
                    Spacer(Modifier.width(8.dp))
                    Text(I18n.translate(I18nKeys.Theme.DARK), color = onSurface, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun languageDisplayName(locale: Locale): String {
    val langKey = I18nKeys.Lang.forLocale(locale)
    val translated = I18n.translate(langKey)
    return if (translated == langKey) {
        locale.getDisplayName(I18n.locale())
    } else {
        translated
    }
}

