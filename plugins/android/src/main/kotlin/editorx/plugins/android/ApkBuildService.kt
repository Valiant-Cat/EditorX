package editorx.plugins.android

import com.android.apksig.ApkSigner
import editorx.core.external.ApkTool
import editorx.core.i18n.I18n
import editorx.core.i18n.I18nKeys
import editorx.core.service.BuildService
import editorx.core.service.BuildResult
import editorx.core.service.BuildStatus
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

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
        // 准备输出文件
        val distDir = File(workspaceRoot, "dist").apply { mkdirs() }
        val baseName = workspaceRoot.name.ifEmpty { "output" }
        var outputApk = File(distDir, "${baseName}-recompiled.apk")
        var index = 1
        while (outputApk.exists()) {
            outputApk = File(distDir, "${baseName}-recompiled-$index.apk")
            index++
        }

        return buildTo(workspaceRoot, outputApk, onProgress)
    }

    fun buildTo(workspaceRoot: File, outputApk: File, onProgress: (String) -> Unit): BuildResult {
        onProgress(I18n.translate(I18nKeys.ToolbarMessage.COMPILING_APK))

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
        return runCatching {
            val (privateKey, certificates) = loadKeyAndCerts(keystore)
            val signerConfig = ApkSigner.SignerConfig.Builder(
                "androiddebugkey",
                privateKey,
                certificates
            ).build()

            val tmpSigned = File(apkFile.parentFile, apkFile.nameWithoutExtension + "-signed.tmp.apk")
            if (tmpSigned.exists()) tmpSigned.delete()

            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(apkFile)
                .setOutputApk(tmpSigned)
                // 保持兼容性：debug 签名默认开启 v1 + v2
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .build()
                .sign()

            // 以“覆盖原文件”的方式对齐 apksigner 行为
            try {
                Files.move(
                    tmpSigned.toPath(),
                    apkFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tmpSigned.toPath(),
                    apkFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            SignResult(true, null)
        }.getOrElse { e ->
            SignResult(false, e.message ?: I18n.translate(I18nKeys.ToolbarMessage.SIGN_EXCEPTION))
        }
    }

    private fun loadKeyAndCerts(keystoreFile: File): Pair<PrivateKey, List<X509Certificate>> {
        val ks = KeyStore.getInstance("JKS")
        keystoreFile.inputStream().use { ks.load(it, "android".toCharArray()) }

        val key = ks.getKey("androiddebugkey", "android".toCharArray()) as? PrivateKey
            ?: throw IllegalStateException(I18n.translate(I18nKeys.ToolbarMessage.KEYSTORE_NOT_FOUND))

        val chain = ks.getCertificateChain("androiddebugkey")
            ?.mapNotNull { it as? X509Certificate }
            ?: emptyList()
        if (chain.isEmpty()) throw IllegalStateException(I18n.translate(I18nKeys.ToolbarMessage.KEYSTORE_NOT_FOUND))

        return key to chain
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

    private data class SignResult(val success: Boolean, val message: String?)
}
