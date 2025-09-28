package editorx.file

import editorx.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for FileType implementations, typically contributed by plugins.
 */
object FileTypeRegistry {
    private val byId = ConcurrentHashMap<String, FileType>()
    private val byExt = ConcurrentHashMap<String, FileType>() // ext lowercased (no dot)

    @Synchronized
    fun register(fileType: FileType) {
        if (byId.containsKey(fileType.getName())) return
        byId[fileType.getName()] = fileType
        fileType.getExtensions().forEach { ext ->
            val key = ext.trim().removePrefix(".").lowercase()
            if (key.isNotEmpty()) byExt[key] = fileType
        }
    }

    fun getById(id: String): FileType? = byId[id]

    fun getByExtension(ext: String?): FileType? =
        ext?.trim()?.removePrefix(".")?.lowercase()?.let { byExt[it] }

    fun getByFile(file: VirtualFile): FileType? = getByExtension(file.extension)

    fun all(): List<FileType> = byId.values.toList()
}

