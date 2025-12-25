package editorx.core.i18n

/**
 * 翻译 key 常量类。
 * 所有翻译 key 都应在此定义，避免在代码中硬编码字符串。
 *
 * 使用方式：
 * ```kotlin
 * I18n.translate(I18nKeys.Menu.FILE)
 * ```
 *
 * 新增翻译 key 时：
 * 1. 在此类中添加对应的常量
 * 2. 在语言包插件中添加翻译
 */
object I18nKeys {

    /**
     * 菜单相关的翻译 key
     */
    object Menu {
        const val FILE = "menu.file"
        const val EDIT = "menu.edit"
        const val PLUGINS = "menu.plugins"
        const val HELP = "menu.help"
        const val LANGUAGE = "menu.language"
    }

    /**
     * 操作相关的翻译 key
     */
    object Action {
        const val OPEN_FILE = "action.openFile"
        const val OPEN_FOLDER = "action.openFolder"
        const val RECENT = "action.recent"
        const val SAVE = "action.save"
        const val SAVE_AS = "action.saveAs"
        const val EXIT = "action.exit"
        const val FIND = "action.find"
        const val REPLACE = "action.replace"
        const val GLOBAL_SEARCH = "action.globalSearch"
        const val PLUGIN_MANAGER = "action.pluginManager"
        const val ABOUT = "action.about"
        const val HELP = "action.help"
    }

    /**
     * 设置相关的翻译 key
     */
    object Settings {
        const val APPEARANCE = "settings.appearance"
        const val LANGUAGE = "settings.language"
        const val THEME = "settings.theme"
        const val APPEARANCE_TIP = "settings.appearance.tip"
    }

    /**
     * 主题相关的翻译 key
     */
    object Theme {
        const val LIGHT = "theme.light"
        const val DARK = "theme.dark"
    }

    /**
     * 语言名称相关的翻译 key。
     * 格式：lang.{locale.toLanguageTag()}
     * 例如：lang.zh-CN, lang.zh-TW, lang.en, lang.ja 等
     */
    object Lang {
        private const val PREFIX = "lang"

        /**
         * 根据 Locale 生成语言名称的 key
         */
        fun forLocale(locale: java.util.Locale): String {
            return "${PREFIX}.${locale.toLanguageTag()}"
        }
    }
}

