package editorx.plugins.android

import editorx.core.external.ApkTool
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.service.BuildService
import editorx.core.service.BuildResult
import editorx.core.service.BuildStatus
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

/**
 * Android 构建提供者
 * 为 apktool 反编译的项目提供构建和签名能力
 */
class ApkBuildService : BuildService {
    companion object {
        private val logger = LoggerFactory.getLogger(ApkBuildService::class.java)
    }

    override fun canBuild(workspaceRoot: File): Boolean {
        // 检查是否是 apktool 反编译的项目（存在 apktool.yml）
        val apktoolConfig = File(workspaceRoot, "apktool.yml")
        return apktoolConfig.exists()
    }

    override fun build(workspaceRoot: File, onProgress: (String) -> Unit): BuildResult {
        onProgress(I18n.translate(I18nKeys.ToolbarMessage.COMPILING_APK))

        // 准备输出文件
        val distDir = File(workspaceRoot, "dist").apply { mkdirs() }
        val baseName = workspaceRoot.name.ifEmpty { "output" }
        var outputApk = File(distDir, "${baseName}-recompiled.apk")
        var index = 1
        while (outputApk.exists()) {
            outputApk = File(distDir, "${baseName}-recompiled-$index.apk")
            index++
        }

        // 使用 ApkTool 构建
        val buildResult = ApkTool.build(workspaceRoot, outputApk)

        when (buildResult.status) {
            ApkTool.Status.SUCCESS -> {
                // 构建成功，进行签名
                onProgress(I18n.translate(I18nKeys.ToolbarMessage.SIGNING_APK))
                val signResult = signWithDebugKeystore(outputApk)
                if (signResult.success) {
                    return BuildResult(
                        status = BuildStatus.SUCCESS,
                        outputFile = outputApk,
                        output = buildResult.output
                    )
                } else {
                    return BuildResult(
                        status = BuildStatus.FAILED,
                        errorMessage = signResult.message ?: I18n.translate(I18nKeys.ToolbarMessage.SIGN_EXCEPTION),
                        outputFile = outputApk, // APK 已生成，但签名失败
                        output = buildResult.output
                    )
                }
            }

            ApkTool.Status.NOT_FOUND -> {
                return BuildResult(
                    status = BuildStatus.NOT_FOUND,
                    errorMessage = I18n.translate(I18nKeys.ToolbarMessage.APKTOOL_NOT_FOUND),
                    output = buildResult.output
                )
            }

            ApkTool.Status.CANCELLED -> {
                return BuildResult(
                    status = BuildStatus.CANCELLED,
                    output = buildResult.output
                )
            }

            ApkTool.Status.FAILED -> {
                return BuildResult(
                    status = BuildStatus.FAILED,
                    errorMessage = I18n.translate(I18nKeys.ToolbarMessage.COMPILE_FAILED)
                        .format(buildResult.exitCode),
                    exitCode = buildResult.exitCode,
                    output = buildResult.output
                )
            }
        }
    }

    private fun signWithDebugKeystore(apkFile: File): SignResult {
        val keystore = ensureDebugKeystore()
            ?: return SignResult(false, I18n.translate(I18nKeys.ToolbarMessage.KEYSTORE_NOT_FOUND))
        val apksigner = locateApkSigner()
            ?: return SignResult(
                false,
                I18n.translate(I18nKeys.ToolbarMessage.APKSIGNER_NOT_FOUND)
            )

        val processBuilder =
            ProcessBuilder(
                apksigner,
                "sign",
                "--ks",
                keystore.absolutePath,
                "--ks-pass",
                "pass:android",
                "--key-pass",
                "pass:android",
                "--ks-key-alias",
                "androiddebugkey",
                apkFile.absolutePath
            )
        processBuilder.redirectErrorStream(true)
        return try {
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) SignResult(true, null)
            else SignResult(false, "apksigner exit code $exitCode\n$output")
        } catch (e: Exception) {
            SignResult(false, e.message ?: I18n.translate(I18nKeys.ToolbarMessage.SIGN_EXCEPTION))
        }
    }

    private fun ensureDebugKeystore(): File? {
        val keystore = File(System.getProperty("user.home"), ".android/debug.keystore")
        if (keystore.exists()) return keystore

        keystore.parentFile?.mkdirs()
        val keytool = locateKeytool() ?: return null
        val processBuilder =
            ProcessBuilder(
                keytool,
                "-genkeypair",
                "-alias",
                "androiddebugkey",
                "-keypass",
                "android",
                "-keystore",
                keystore.absolutePath,
                "-storepass",
                "android",
                "-dname",
                "CN=Android Debug,O=Android,C=US",
                "-validity",
                "9999",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048"
            )
        processBuilder.redirectErrorStream(true)
        return try {
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0 && keystore.exists()) {
                keystore
            } else {
                logger.warn("keytool 生成调试签名失败，输出: {}", output)
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun locateKeytool(): String? {
        try {
            val process = ProcessBuilder("keytool", "-help").start()
            process.waitFor()
            return "keytool"
        } catch (_: Exception) {
        }

        val javaHome = System.getProperty("java.home")
        if (!javaHome.isNullOrEmpty()) {
            val bin = File(javaHome, "bin/keytool")
            if (bin.exists()) return bin.absolutePath
            val binWin = File(javaHome, "bin/keytool.exe")
            if (binWin.exists()) return binWin.absolutePath
        }
        return null
    }

    private fun locateApkSigner(): String? {
        val projectRoot = File(System.getProperty("user.dir"))
        val local = File(projectRoot, "tools/apksigner")
        if (local.exists() && local.canExecute()) return local.absolutePath

        try {
            val process = ProcessBuilder("apksigner", "--version").start()
            if (process.waitFor() == 0) return "apksigner"
        } catch (_: Exception) {
        }

        val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (!sdkRoot.isNullOrBlank()) {
            val buildTools = File(sdkRoot, "build-tools")
            if (buildTools.isDirectory) {
                val candidates = buildTools.listFiles()?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
                if (candidates != null) {
                    for (dir in candidates) {
                        val exe = File(dir, "apksigner")
                        if (exe.exists()) return exe.absolutePath
                        val exeWin = File(dir, "apksigner.bat")
                        if (exeWin.exists()) return exeWin.absolutePath
                    }
                }
            }
        }
        return null
    }

    private data class SignResult(val success: Boolean, val message: String?)
}

