package editorx.plugins.apk

import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.service.BuildResult
import editorx.core.service.BuildService
import editorx.core.service.BuildStatus
import org.slf4j.LoggerFactory
import java.io.File

class XapkBuildService : BuildService {
    companion object {
        private val logger = LoggerFactory.getLogger(XapkBuildService::class.java)
    }

    override fun canBuild(workspaceRoot: File): Boolean {
        return XapkWorkspaceMarker.isXapkWorkspace(workspaceRoot)
    }

    override fun build(workspaceRoot: File, onProgress: (String) -> Unit): BuildResult {
        onProgress(I18n.translate(I18nKeys.ToolbarMessage.PACKING_XAPK))

        val distDir = File(workspaceRoot, "dist").apply { mkdirs() }
        val baseName = XapkWorkspaceMarker.readOriginFileName(workspaceRoot)
            ?.removeSuffix(".xapk")
            ?: workspaceRoot.name.ifEmpty { "output" }

        var outputXapk = File(distDir, "${baseName}-repacked.xapk")
        var index = 1
        while (outputXapk.exists()) {
            outputXapk = File(distDir, "${baseName}-repacked-$index.xapk")
            index++
        }

        return try {
            val apkEntries = XapkWorkspaceMarker.readApkEntries(workspaceRoot).ifEmpty {
                scanApkEntries(workspaceRoot)
            }
            val rebuildResult = rebuildApksIfNeeded(workspaceRoot, apkEntries, onProgress)
            if (rebuildResult != null) return rebuildResult

            packWorkspace(workspaceRoot, outputXapk)
            BuildResult(status = BuildStatus.SUCCESS, outputFile = outputXapk)
        } catch (e: Exception) {
            logger.error("XAPK 打包失败", e)
            BuildResult(
                status = BuildStatus.FAILED,
                errorMessage = I18n.translate(I18nKeys.ToolbarMessage.PACK_XAPK_FAILED)
                    .format(e.message ?: "unknown"),
                outputFile = outputXapk
            )
        }
    }

    private fun rebuildApksIfNeeded(
        workspaceRoot: File,
        apkEntries: List<String>,
        onProgress: (String) -> Unit
    ): BuildResult? {
        if (apkEntries.isEmpty()) return null
        val builder = ApkBuildService()

        apkEntries.forEach { entry ->
            val apkFile = File(workspaceRoot, entry)
            if (!apkFile.isFile) return@forEach

            val projectDir = File(apkFile.parentFile, apkFile.nameWithoutExtension)
            if (!File(projectDir, "apktool.yml").isFile) return@forEach

            onProgress(I18n.translate(I18nKeys.ToolbarMessage.COMPILING_APK))
            val result = builder.buildTo(projectDir, apkFile, onProgress)
            if (result.status != BuildStatus.SUCCESS) {
                return when (result.status) {
                    BuildStatus.NOT_FOUND -> BuildResult(
                        status = BuildStatus.NOT_FOUND,
                        errorMessage = result.errorMessage
                            ?: I18n.translate(I18nKeys.ToolbarMessage.APKTOOL_NOT_FOUND)
                    )
                    BuildStatus.CANCELLED -> BuildResult(status = BuildStatus.CANCELLED)
                    BuildStatus.FAILED -> BuildResult(
                        status = BuildStatus.FAILED,
                        errorMessage = result.errorMessage
                            ?: I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED)
                                .format(result.exitCode ?: -1)
                    )
                    BuildStatus.SUCCESS -> result
                }
            }
        }
        return null
    }

    private fun packWorkspace(workspaceRoot: File, outputXapk: File) {
        val skipRoots = findApktoolRoots(workspaceRoot)
        ArchiveUtils.packZip(workspaceRoot, outputXapk) { relative, file ->
            if (relative.startsWith("dist/")) return@packZip true
            if (relative == XapkWorkspaceMarker.MARKER_FILE) return@packZip true
            skipRoots.any { root -> file.canonicalPath.startsWith(root.canonicalPath + File.separator) }
        }
    }

    private fun findApktoolRoots(workspaceRoot: File): List<File> {
        return workspaceRoot.walkTopDown()
            .filter { it.isFile && it.name == "apktool.yml" }
            .map { it.parentFile }
            .distinct()
            .toList()
    }

    private fun scanApkEntries(workspaceRoot: File): List<String> {
        val root = workspaceRoot.canonicalFile.toPath()
        return workspaceRoot.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .filterNot { it.canonicalPath.startsWith(File(workspaceRoot, "dist").canonicalPath + File.separator) }
            .mapNotNull {
                runCatching { root.relativize(it.canonicalFile.toPath()).toString().replace('\\', '/') }.getOrNull()
            }
            .sorted()
            .toList()
    }
}
