package editorx.core.i18n

import java.util.*

/**
 * 简单的国际化服务：
 * - 支持插件注册/卸载翻译（一个语言包插件只对应一种语言）；
 * - 支持运行时切换语言；
 * - 实时翻译，每次调用 translate 时从提供器获取最新翻译。
 */
object I18n {

    private val providers: MutableMap<Locale, TranslationProvider> = mutableMapOf()
    private val listeners: MutableList<() -> Unit> = mutableListOf()

    @Volatile
    private var currentLocale: Locale = Locale.SIMPLIFIED_CHINESE

    fun locale(): Locale = currentLocale

    fun setLocale(locale: Locale) {
        if (locale == currentLocale) return
        currentLocale = locale
        fireLanguageChanged()
    }

    /**
     * 注册翻译提供器。
     * 如果该语言已存在提供器，后注册的会覆盖先注册的。
     *
     * @param locale 该提供器对应的语言（一个提供器只对应一种语言）
     * @param provider 翻译提供器
     */
    fun register(locale: Locale, provider: TranslationProvider) {
        providers[locale] = provider
        fireLanguageChanged()
    }

    /**
     * 注销指定的翻译提供器。
     *
     * @param provider 要注销的提供器实例
     */
    fun unregister(provider: TranslationProvider) {
        val changed = providers.entries.removeIf { it.value == provider }
        if (!changed) return
        fireLanguageChanged()
    }

    /**
     * 获取翻译。
     *
     *  @param key 翻译 key
     * @return 翻译文本，如果都找不到则返回 key
     */
    fun translate(key: String): String {

        val currentProvider = providers[currentLocale]
        if (currentProvider != null) {
            val result = runCatching { currentProvider.translate(key) }.getOrDefault(null)
            if (result != null) return result
        }

        // 找不到，返回 key
        return key
    }

    /**
     * 获取所有已注册的语言。
     *
     * @return 已注册的语言列表
     */
    fun getAvailableLocales(): List<Locale> {
        return providers.keys.sortedWith(compareBy { it.toLanguageTag() })
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun fireLanguageChanged() {
        listeners.forEach { listener -> runCatching { listener() } }
    }
}

