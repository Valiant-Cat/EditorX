package editorx.core.store

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

class FileStore(private val file: File) : Store {
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

