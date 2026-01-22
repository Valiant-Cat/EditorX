package editorx.plugins.apk

import editorx.core.external.Smali
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.service.BuildResult
import editorx.core.service.BuildService
import editorx.core.service.BuildStatus
import org.slf4j.LoggerFactory
import java.io.File

class AabBuildService : BuildService {
    companion object {
        private val logger = LoggerFactory.getLogger(AabBuildService::class.java)
    }

    override fun canBuild(workspaceRoot: File): Boolean {
        return AabWorkspaceMarker.isAabWorkspace(workspaceRoot)
    }

    override fun build(workspaceRoot: File, onProgress: (String) -> Unit): BuildResult {
        onProgress(I18n.translate(I18nKeys.ToolbarMessage.PACKING_AAB))

        val distDir = File(workspaceRoot, "dist").apply { mkdirs() }
        val baseName = AabWorkspaceMarker.readOriginFileName(workspaceRoot)
            ?.removeSuffix(".aab")
            ?: workspaceRoot.name.ifEmpty { "output" }

        var outputAab = File(distDir, "${baseName}-repacked.aab")
        var index = 1
        while (outputAab.exists()) {
            outputAab = File(distDir, "${baseName}-repacked-$index.aab")
            index++
        }

        return try {
            val rebuildResult = rebuildDexIfNeeded(workspaceRoot, onProgress)
            if (rebuildResult != null) return rebuildResult

            packWorkspace(workspaceRoot, outputAab)
            BuildResult(status = BuildStatus.SUCCESS, outputFile = outputAab)
        } catch (e: Exception) {
            logger.error("AAB 打包失败", e)
            BuildResult(
                status = BuildStatus.FAILED,
                errorMessage = I18n.translate(I18nKeys.ToolbarMessage.PACK_AAB_FAILED)
                    .format(e.message ?: "unknown"),
                outputFile = outputAab
            )
        }
    }

    private fun rebuildDexIfNeeded(workspaceRoot: File, onProgress: (String) -> Unit): BuildResult? {
        val moduleDirs = workspaceRoot.listFiles { file ->
            file.isDirectory && File(file, "dex").isDirectory
        }?.sortedBy { it.name } ?: emptyList()

        val smaliTargets = moduleDirs.flatMap { module ->
            module.listFiles { file ->
                file.isDirectory && file.name.matches(Regex("""^smali(_classes\\d+)?$"""))
            }?.toList() ?: emptyList()
        }

        if (smaliTargets.isEmpty()) return null

        if (Smali.locate() == null) {
            return BuildResult(
                status = BuildStatus.NOT_FOUND,
                errorMessage = I18n.translate(I18nKeys.ToolbarMessage.SMALI_NOT_FOUND)
            )
        }

        onProgress(I18n.translate(I18nKeys.ToolbarMessage.REBUILDING_AAB_DEX))

        moduleDirs.forEach { module ->
            val smaliDirs = module.listFiles { file ->
                file.isDirectory && file.name.matches(Regex("""^smali(_classes\\d+)?$"""))
            }?.sortedBy { it.name } ?: emptyList()
            if (smaliDirs.isEmpty()) return@forEach

            val dexDir = File(module, "dex").apply { mkdirs() }
            smaliDirs.forEach { smaliDir ->
                val dexName = if (smaliDir.name == "smali") {
                    "classes.dex"
                } else {
                    val suffix = smaliDir.name.removePrefix("smali_classes")
                    val index = suffix.toIntOrNull() ?: 2
                    "classes$index.dex"
                }
                val dexFile = File(dexDir, dexName)
                val result = Smali.assembleDir(smaliDir, dexFile)
                if (result.status != Smali.Status.SUCCESS) {
                    return BuildResult(
                        status = BuildStatus.FAILED,
                        errorMessage = I18n.translate(I18nKeys.ToolbarMessage.REBUILD_AAB_DEX_FAILED)
                            .format(result.output)
                    )
                }
            }
        }
        return null
    }

    private fun packWorkspace(workspaceRoot: File, outputAab: File) {
        ArchiveUtils.packZip(workspaceRoot, outputAab) { relative, _ ->
            if (relative.startsWith("dist/")) return@packZip true
            if (relative == AabWorkspaceMarker.MARKER_FILE) return@packZip true
            if (relative.contains("/smali/") || relative.contains("/smali_classes")) return@packZip true
            false
        }
    }
}
