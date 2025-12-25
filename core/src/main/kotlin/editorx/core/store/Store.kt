package editorx.core.store

/**
 * Simple persistent key-value settings store backed by a .properties file.
 * Keys are flat dot-separated strings (e.g. editor.theme, files.recent.0).
 */
interface Store {
    fun get(key: String, default: String? = null): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(prefix: String? = null): List<String>
    fun sync()
}