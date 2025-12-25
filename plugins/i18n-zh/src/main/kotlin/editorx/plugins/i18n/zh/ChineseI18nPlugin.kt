package editorx.plugins.i18n.zh

import editorx.core.i18n.I18nPlugin
import editorx.core.plugin.PluginInfo
import java.util.Locale

private val dictionary = mapOf(
    "menu.file" to "文件",
    "menu.edit" to "编辑",
    "menu.plugins" to "插件",
    "menu.help" to "帮助",
    "menu.language" to "语言",

    "action.openFile" to "打开文件…",
    "action.openFolder" to "打开文件夹…",
    "action.recent" to "最近打开",
    "action.save" to "保存",
    "action.saveAs" to "另存为…",
    "action.exit" to "退出",

    "action.find" to "查找…",
    "action.replace" to "替换…",
    "action.globalSearch" to "在文件中搜索…",

    "action.pluginManager" to "插件管理",
    "action.about" to "关于",
    "action.help" to "帮助文档",

    "settings.appearance" to "外观",
    "settings.language" to "语言",
    "settings.theme" to "主题",
    "theme.light" to "浅色",
    "theme.dark" to "深色",
    "settings.appearance.tip" to "提示：语言和主题切换立即生效。",

    "lang.zh" to "中文",
    "lang.zh-CN" to "中文（简体）",
    "lang.zh-TW" to "中文（繁体）",
    "lang.en" to "English",
)

class ChineseI18nPlugin : I18nPlugin(Locale.SIMPLIFIED_CHINESE) {

    override fun getInfo() = PluginInfo(
        id = "i18n-zh",
        name = "Chinese (i18n)",
        version = "0.0.1",
    )

    override fun translate(key: String): String? = dictionary[key]
}

