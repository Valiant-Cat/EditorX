package editorx.plugins.i18n.en

import editorx.core.i18n.I18n
import editorx.core.i18n.TranslationProvider
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo
import java.util.Locale

class EnglishI18nPlugin : Plugin {

    override fun getInfo(): PluginInfo = PluginInfo(
        id = "i18n-en",
        name = "English (i18n)",
        version = "0.0.1",
    )

    private val provider = TranslationProvider { locale ->
        if (locale.language != Locale.ENGLISH.language) return@TranslationProvider emptyMap()
        mapOf(
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

            "lang.zh" to "Chinese",
            "lang.en" to "English",
        )
    }

    override fun activate(pluginContext: PluginContext) {
        I18n.register(provider, ownerId = pluginContext.pluginId())
    }
}

