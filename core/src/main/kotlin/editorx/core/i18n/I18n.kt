package editorx.core.i18n

import java.util.Locale

/**
 * 简单的国际化服务：
 * - 支持插件注册/卸载翻译；
 * - 支持运行时切换语言；
 * - 以“后注册覆盖先注册”的方式合并 key。
 */
object I18n {

    private data class Registration(
        val ownerId: String?,
        val provider: TranslationProvider,
    )

    private val providers: MutableList<Registration> = mutableListOf()
    private val listeners: MutableList<() -> Unit> = mutableListOf()

    @Volatile
    private var currentLocale: Locale = Locale.SIMPLIFIED_CHINESE

    @Volatile
    private var dictionary: Map<String, String> = emptyMap()

    fun locale(): Locale = currentLocale

    fun setLocale(locale: Locale) {
        if (locale == currentLocale) return
        currentLocale = locale
        rebuild()
        fireLanguageChanged()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun register(provider: TranslationProvider, ownerId: String? = null) {
        providers.add(Registration(ownerId = ownerId, provider = provider))
        rebuild()
        fireLanguageChanged()
    }

    fun unregisterByOwner(ownerId: String) {
        val changed = providers.removeIf { it.ownerId == ownerId }
        if (!changed) return
        rebuild()
        fireLanguageChanged()
    }

    /**
     * 获取翻译；若不存在则返回 defaultText。
     */
    fun translate(key: String, defaultText: String): String {
        return dictionary[key] ?: defaultText
    }

    private fun rebuild() {
        val locale = currentLocale
        val merged = LinkedHashMap<String, String>()
        providers.forEach { reg ->
            val map = runCatching { reg.provider.translations(locale) }.getOrDefault(emptyMap())
            merged.putAll(map)
        }
        dictionary = merged
    }

    private fun fireLanguageChanged() {
        listeners.forEach { listener -> runCatching { listener() } }
    }
}

