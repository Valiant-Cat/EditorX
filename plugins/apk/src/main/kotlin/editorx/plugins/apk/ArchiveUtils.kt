package editorx.plugins.apk

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ArchiveUtils {
    fun extractZip(archiveFile: File, outputDir: File) {
        val outputRoot = outputDir.canonicalFile
        ZipFile(archiveFile).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(outputDir, entry.name)
                val canonicalOut = outFile.canonicalFile
                if (!canonicalOut.path.startsWith(outputRoot.path + File.separator)) {
                    throw IllegalStateException("zip entry is outside target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    fun packZip(
        rootDir: File,
        outputZip: File,
        shouldSkip: (relativePath: String, file: File) -> Boolean
    ) {
        val rootPath = rootDir.canonicalFile.toPath()
        ZipOutputStream(FileOutputStream(outputZip)).use { zipOut ->
            rootDir.walkTopDown().forEach { file ->
                val relative = runCatching { rootPath.relativize(file.canonicalFile.toPath()).toString() }
                    .getOrDefault("")
                if (relative.isEmpty()) return@forEach
                val normalized = relative.replace('\\', '/')
                if (shouldSkip(normalized, file)) return@forEach

                if (file.isDirectory) {
                    val entryName = if (normalized.endsWith("/")) normalized else "$normalized/"
                    zipOut.putNextEntry(ZipEntry(entryName))
                    zipOut.closeEntry()
                } else {
                    zipOut.putNextEntry(ZipEntry(normalized))
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
    }
}
