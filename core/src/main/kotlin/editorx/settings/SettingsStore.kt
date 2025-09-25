package editorx.settings

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Simple persistent key-value settings store backed by a .properties file.
 * Keys are flat dot-separated strings (e.g. editor.theme, files.recent.0).
 */
interface SettingsStore {
    fun get(key: String, default: String? = null): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(prefix: String? = null): List<String>
    fun sync()
}

class PropertiesSettingsStore(private val file: File) : SettingsStore {
    private val props = Properties()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        runCatching { FileInputStream(file).use { props.load(it) } }
    }

    override fun get(key: String, default: String?): String? {
        ensureLoaded()
        return props.getProperty(key, default)
    }

    override fun put(key: String, value: String) {
        ensureLoaded()
        props.setProperty(key, value)
    }

    override fun remove(key: String) {
        ensureLoaded()
        props.remove(key)
    }

    override fun keys(prefix: String?): List<String> {
        ensureLoaded()
        val all = props.keys().toList().map { it.toString() }
        return if (prefix == null) all else all.filter { it.startsWith(prefix) }
    }

    override fun sync() {
        ensureLoaded()
        file.parentFile?.mkdirs()
        runCatching { FileOutputStream(file).use { props.store(it, "EditorX Settings") } }
    }
}

