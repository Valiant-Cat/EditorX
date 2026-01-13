package editorx.gui.update

import editorx.core.util.SystemUtils
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

object SelfUpdater {
    private val logger = LoggerFactory.getLogger(SelfUpdater::class.java)

    data class ApplyResult(
        val success: Boolean,
        val message: String,
        val restartScript: Path? = null,
    )

    fun applyDownloadedPackage(zipFile: Path, currentPid: Long): ApplyResult {
        if (!zipFile.exists()) return ApplyResult(false, "更新包不存在：${zipFile.absolutePathString()}")

        if (!SystemUtils.isMacOS()) {
            return ApplyResult(false, "当前仅实现 macOS 自动更新（已下载：${zipFile.absolutePathString()}）")
        }

        val bundlePath = VersionUtil.appBundlePathOrNull()
            ?: return ApplyResult(false, "当前不是从 macOS .app 运行，无法自动替换自身（已下载：${zipFile.absolutePathString()}）")

        val parentDir = bundlePath.parent
        if (parentDir == null) {
            return ApplyResult(
                false,
                "无法确定应用目录，无法自动更新。\n已下载更新包：${zipFile.absolutePathString()}\n请手动解压并替换当前应用。"
            )
        }

        val targetApp = resolveTargetApp(bundlePath, parentDir) ?: run {
            return ApplyResult(
                false,
                "应用目录不可写，无法自动更新。\n已下载更新包：${zipFile.absolutePathString()}\n" +
                    "你可以：\n" +
                    "1) 将 EditorX.app 放到 ~/Applications（推荐，自动更新可用）\n" +
                    "2) 或手动解压并替换当前应用（若在 /Applications 可能需要管理员权限）。"
            )
        }

        val extractDir = Files.createTempDirectory("editorx_update_")
        val ditto = Path.of("/usr/bin/ditto")
        if (!ditto.exists()) return ApplyResult(false, "缺少 /usr/bin/ditto，无法解压更新包")

        val unzip = ProcessBuilder(
            ditto.absolutePathString(),
            "-x",
            "-k",
            zipFile.absolutePathString(),
            extractDir.absolutePathString()
        ).start()
        val unzipExit = unzip.waitFor()
        if (unzipExit != 0) {
            val out = unzip.inputStream.bufferedReader().use { it.readText() }
            return ApplyResult(false, "解压失败（exit=$unzipExit）：$out")
        }

        val newApp = Files.list(extractDir).use { stream ->
            stream.filter { it.name.endsWith(".app") && it.isDirectory() }.findFirst().orElse(null)
        } ?: run {
            return ApplyResult(false, "解压后未找到 .app 目录")
        }

        val helper = writeHelperScript(extractDir, currentPid, targetApp, newApp)
        val msg = if (targetApp == bundlePath) {
            "更新包已准备完成。\n点击“立即重启”完成更新并启动新版本。"
        } else {
            "当前应用目录不可写，已将新版本安装到：${targetApp.absolutePathString()}\n点击“立即重启”打开新版本。"
        }
        return ApplyResult(true, msg, helper)
    }

    fun startPreparedUpdate(script: Path): ApplyResult {
        if (!script.exists()) return ApplyResult(false, "更新脚本不存在：${script.absolutePathString()}")
        val workDir = script.parent ?: return ApplyResult(false, "无法解析更新脚本目录：${script.absolutePathString()}")

        runCatching {
            ProcessBuilder("/bin/bash", script.absolutePathString())
                .directory(workDir.toFile())
                .start()
        }.onFailure { e ->
            logger.warn("启动更新脚本失败", e)
            return ApplyResult(false, "启动更新脚本失败：${e.message}")
        }

        return ApplyResult(true, "正在更新…")
    }

    private fun resolveTargetApp(currentApp: Path, currentParent: Path): Path? {
        if (Files.isWritable(currentParent)) return currentApp

        val home = System.getProperty("user.home")?.trim().orEmpty()
        if (home.isEmpty()) return null

        val userApplications = Path.of(home, "Applications")
        runCatching { Files.createDirectories(userApplications) }
        if (!Files.isWritable(userApplications)) return null

        return userApplications.resolve(currentApp.fileName.toString())
    }

    private fun writeHelperScript(extractDir: Path, pid: Long, targetApp: Path, newApp: Path): Path {
        val script = extractDir.resolve("apply_update.sh")
        val content = """
            #!/bin/bash
            set -e
            
            PID="${pid}"
            TARGET_APP="${targetApp.absolutePathString()}"
            NEW_APP="${newApp.absolutePathString()}"
            
            # 等待旧进程退出
            while kill -0 "${'$'}PID" >/dev/null 2>&1; do
              sleep 0.2
            done
            
            TS="$(date +%s)"
            TMP="${'$'}{TARGET_APP}.tmp.${'$'}{TS}"
            
            # 不备份旧版本：先复制到临时目录，成功后再替换
            /usr/bin/ditto "${'$'}NEW_APP" "${'$'}TMP"
            rm -rf "${'$'}TARGET_APP" || true
            mv "${'$'}TMP" "${'$'}TARGET_APP"
            
            # 清理解压产物（失败也无所谓）
            rm -rf "${'$'}NEW_APP" || true
            
            open "${'$'}TARGET_APP" || true
        """.trimIndent()
        Files.writeString(script, content)
        script.toFile().setExecutable(true)
        return script
    }
}
