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

    @Volatile
    private var currentLocale: Locale = Locale.SIMPLIFIED_CHINESE

    private val registrations: MutableList<Registration> = mutableListOf()
    private val listeners: MutableList<() -> Unit> = mutableListOf()

    @Volatile
    private var dictionary: Map<String, String> = emptyMap()

    fun locale(): Locale = currentLocale

    fun setLocale(locale: Locale) {
        if (locale == currentLocale) return
        currentLocale = locale
        rebuild()
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun register(provider: TranslationProvider, ownerId: String? = null) {
        registrations.add(Registration(ownerId = ownerId, provider = provider))
        rebuild()
        notifyListeners()
    }

    fun unregisterByOwner(ownerId: String) {
        val changed = registrations.removeIf { it.ownerId == ownerId }
        if (!changed) return
        rebuild()
        notifyListeners()
    }

    /**
     * 获取翻译；若不存在则返回 defaultText。
     */
    fun t(key: String, defaultText: String): String {
        return dictionary[key] ?: defaultText
    }

    private fun rebuild() {
        val locale = currentLocale
        val merged = LinkedHashMap<String, String>()
        registrations.forEach { reg ->
            val map = runCatching { reg.provider.translations(locale) }.getOrDefault(emptyMap())
            merged.putAll(map)
        }
        dictionary = merged
    }

    private fun notifyListeners() {
        listeners.forEach { listener -> runCatching { listener() } }
    }
}

