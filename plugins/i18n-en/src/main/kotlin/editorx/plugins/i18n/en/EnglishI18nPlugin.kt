package editorx.plugins.i18n.en

import editorx.core.i18n.I18nPlugin
import editorx.core.plugin.PluginInfo
import java.util.Locale

private val dictionary = mapOf(
    "menu.file" to "File",
    "menu.edit" to "Edit",
    "menu.plugins" to "Plugins",
    "menu.help" to "Help",
    "menu.language" to "Language",

    "action.openFile" to "Open File…",
    "action.openFolder" to "Open Folder…",
    "action.recent" to "Recent Files",
    "action.save" to "Save",
    "action.saveAs" to "Save As…",
    "action.exit" to "Quit",
    "action.find" to "Find…",
    "action.replace" to "Replace…",
    "action.globalSearch" to "Search in Files…",
    "action.pluginManager" to "Plugin Manager",
    "action.about" to "About",
    "action.help" to "Help",

    "settings.appearance" to "Appearance",
    "settings.language" to "Language",
    "settings.theme" to "Theme",
    "theme.light" to "Light",
    "theme.dark" to "Dark",
    "settings.appearance.tip" to "Tip: language and theme changes apply immediately.",

    "lang.zh" to "Chinese",
    "lang.zh-CN" to "Chinese (Simplified)",
    "lang.zh-TW" to "Chinese (Traditional)",
    "lang.en" to "English",
)

class EnglishI18nPlugin : I18nPlugin(Locale.ENGLISH) {

    override fun getInfo() = PluginInfo(
        id = "i18n-en",
        name = "English (i18n)",
        version = "0.0.1",
    )

    override fun translate(key: String): String? = dictionary[key]
}

