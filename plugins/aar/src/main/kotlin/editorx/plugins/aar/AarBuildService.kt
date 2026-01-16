package editorx.plugins.aar

import editorx.core.external.Dex2Jar
import editorx.core.external.Smali
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.service.BuildResult
import editorx.core.service.BuildService
import editorx.core.service.BuildStatus
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AarBuildService : BuildService {
    companion object {
        private val logger = LoggerFactory.getLogger(AarBuildService::class.java)
    }

    override fun canBuild(workspaceRoot: File): Boolean {
        return AarWorkspaceMarker.isAarWorkspace(workspaceRoot)
    }

    override fun build(workspaceRoot: File, onProgress: (String) -> Unit): BuildResult {
        onProgress(I18n.translate(I18nKeys.ToolbarMessage.PACKING_AAR))

        val distDir = File(workspaceRoot, "dist").apply { mkdirs() }
        val baseName = AarWorkspaceMarker.readOriginFileName(workspaceRoot)?.removeSuffix(".aar")
            ?: workspaceRoot.name.ifEmpty { "output" }

        var outputAar = File(distDir, "${baseName}-repacked.aar")
        var index = 1
        while (outputAar.exists()) {
            outputAar = File(distDir, "${baseName}-repacked-$index.aar")
            index++
        }

        return try {
            val rebuildResult = rebuildClassesJarIfNeeded(workspaceRoot, onProgress)
            if (rebuildResult != null && !rebuildResult.success) {
                return BuildResult(
                    status = BuildStatus.FAILED,
                    errorMessage = I18n.translate(I18nKeys.ToolbarMessage.REBUILD_AAR_CLASSES_FAILED)
                        .format(rebuildResult.message ?: "unknown"),
                )
            }
            packWorkspace(workspaceRoot, outputAar)
            BuildResult(
                status = BuildStatus.SUCCESS,
                outputFile = outputAar
            )
        } catch (e: Exception) {
            logger.error("AAR 打包失败", e)
            BuildResult(
                status = BuildStatus.FAILED,
                errorMessage = I18n.translate(I18nKeys.ToolbarMessage.PACK_AAR_FAILED)
                    .format(e.message ?: "unknown"),
                outputFile = outputAar
            )
        }
    }

    private fun packWorkspace(workspaceRoot: File, outputAar: File) {
        val rootPath = workspaceRoot.canonicalFile.toPath()
        ZipOutputStream(FileOutputStream(outputAar)).use { zipOut ->
            workspaceRoot.walkTopDown().forEach { file ->
                val relative = runCatching { rootPath.relativize(file.canonicalFile.toPath()).toString() }
                    .getOrDefault("")
                if (relative.isEmpty()) return@forEach
                val normalized = relative.replace('\\', '/')
                if (shouldSkip(normalized)) return@forEach

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

    private data class RebuildResult(val success: Boolean, val message: String? = null)

    private fun rebuildClassesJarIfNeeded(workspaceRoot: File, onProgress: (String) -> Unit): RebuildResult? {
        val smaliDirs = workspaceRoot.listFiles()?.filter {
            it.isDirectory && it.name.matches("""^smali(_classes\\d+)?$""".toRegex())
        }?.sortedBy { it.name } ?: emptyList()
        if (smaliDirs.isEmpty()) return null

        onProgress(I18n.translate(I18nKeys.ToolbarMessage.REBUILDING_AAR_CLASSES))

        val smaliPath = Smali.locate()
        if (smaliPath == null) return RebuildResult(false, "smali not found")

        val tempDir = kotlin.runCatching { kotlin.io.path.createTempDirectory("aar_smali_build_").toFile() }
            .getOrElse { return RebuildResult(false, it.message) }

        val dexFiles = mutableListOf<File>()
        val jarFiles = mutableListOf<File>()
        try {
            smaliDirs.forEachIndexed { index, dir ->
                val dexFile = File(tempDir, "classes${index + 1}.dex")
                val smaliResult = Smali.assembleDir(dir, dexFile)
                if (smaliResult.status != Smali.Status.SUCCESS) {
                    return RebuildResult(false, smaliResult.output)
                }
                dexFiles.add(dexFile)

                val jarFile = File(tempDir, "classes${index + 1}.jar")
                val jarResult = Dex2Jar.convert(dexFile, jarFile)
                if (jarResult.status != Dex2Jar.Status.SUCCESS) {
                    return RebuildResult(false, jarResult.output)
                }
                jarFiles.add(jarFile)
            }

            if (jarFiles.isEmpty()) return RebuildResult(false, "no jars generated")

            val outputJar = File(workspaceRoot, "classes.jar")
            val backupJar = if (outputJar.exists()) File(workspaceRoot, "classes.jar.bak") else null
            if (backupJar != null) {
                outputJar.copyTo(backupJar, overwrite = true)
            }

            mergeJars(jarFiles, outputJar)
            backupJar?.delete()
            return RebuildResult(true)
        } catch (e: Exception) {
            return RebuildResult(false, e.message)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun mergeJars(jars: List<File>, outputJar: File) {
        if (outputJar.exists()) outputJar.delete()
        val seen = HashSet<String>()
        JarOutputStream(FileOutputStream(outputJar)).use { jarOut ->
            jars.forEach { jar ->
                JarFile(jar).use { jarFile ->
                    val entries = jarFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (entry.isDirectory) continue
                        if (name == "META-INF/MANIFEST.MF") continue
                        if (!seen.add(name)) continue
                        jarOut.putNextEntry(JarEntry(name))
                        jarFile.getInputStream(entry).use { it.copyTo(jarOut) }
                        jarOut.closeEntry()
                    }
                }
            }
        }
    }

    private fun shouldSkip(relativePath: String): Boolean {
        if (relativePath.startsWith("dist/")) return true
        if (relativePath.startsWith("sources/")) return true
        if (relativePath.startsWith("smali/")) return true
        if (relativePath.startsWith("smali_classes")) return true
        if (relativePath == AarWorkspaceMarker.MARKER_FILE) return true
        return false
    }
}
