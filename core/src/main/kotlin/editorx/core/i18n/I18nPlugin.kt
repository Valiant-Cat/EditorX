package editorx.core.i18n

import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import java.util.*

/**
 * 语言包插件基类。
 * 封装了语言包插件的通用逻辑，子类只需实现 [translate] 方法。
 *
 * @param locale 该语言包对应的语言
 */
abstract class I18nPlugin(
    private val locale: Locale
) : Plugin {

    private val provider: TranslationProvider

    init {
        provider = TranslationProvider { key -> translate(key) }
        I18n.register(locale, provider)
    }

    override fun activate(pluginContext: PluginContext) {
        // nop
    }

    override fun deactivate() {
        I18n.unregister(provider)
    }

    /**
     * 翻译指定的 key。
     * 子类实现此方法返回该 key 对应的翻译文案，如果不存在则返回 null。
     *
     * @param key 翻译 key
     * @return 翻译文案，如果不存在则返回 null
     */
    abstract fun translate(key: String): String?
}

