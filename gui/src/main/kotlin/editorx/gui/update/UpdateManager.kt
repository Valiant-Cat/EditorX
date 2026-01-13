package editorx.gui.update

import editorx.core.util.SystemUtils
import editorx.gui.MainWindow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

object UpdateManager {
    private val logger = LoggerFactory.getLogger(UpdateManager::class.java)

    private const val REPO = "Valiant-Cat/EditorX"
    private const val MANIFEST_ASSET_NAME = "manifest.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseName: String?,
        val releaseNotes: String?,
        val releasePageUrl: String?,
        val assetName: String,
        val downloadUrl: String,
    )

    private val started = AtomicBoolean(false)
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    fun checkUpdate(mainWindow: MainWindow) {
        if (!started.compareAndSet(false, true)) return
        Thread {
            checkUpdateInternal(mainWindow)
        }.apply {
            isDaemon = true
            name = "UpdateChecker"
            start()
        }
    }

    private fun checkUpdateInternal(mainWindow: MainWindow) {
        val current = VersionUtil.currentVersion()
        val platform = currentPlatformKey()

        // 优先读取 release 附带的 manifest.json，避免 GitHub REST API 的匿名请求限流（403 rate limit）
        fetchLatestFromManifest(REPO, platform)?.let { latest ->
            val latestVersion = VersionUtil.normalizeTag(latest.version)
            val hasUpdate = VersionUtil.isNewer(current, latestVersion)
            if (!hasUpdate) return

            val info = UpdateAvailable(
                currentVersion = current,
                latestVersion = latestVersion,
                releaseName = null,
                releaseNotes = null,
                releasePageUrl = latest.releasePageUrl,
                assetName = latest.assetName,
                downloadUrl = latest.downloadUrl,
            )

            SwingUtilities.invokeLater {
                mainWindow.statusBar.setUpdateHint("有更新：$latestVersion") {
                    onUpdateClicked(mainWindow, info)
                }
            }
            return
        }

        // 兜底：使用 GitHub API（可能触发匿名限流）
        val latest = GitHubReleaseApi.fetchLatestRelease(REPO) ?: return

        val latestVersion = VersionUtil.normalizeTag(latest.tagName)
        val hasUpdate = VersionUtil.isNewer(current, latestVersion)
        if (!hasUpdate) return

        val asset = selectAsset(latest) ?: run {
            logger.info("发现新版本，但未找到可下载资源：{}", latest.assets.map { it.name })
            return
        }

        val info = UpdateAvailable(
            currentVersion = current,
            latestVersion = latestVersion,
            releaseName = latest.name,
            releaseNotes = latest.body,
            releasePageUrl = latest.htmlUrl,
            assetName = asset.name,
            downloadUrl = asset.browserDownloadUrl,
        )

        SwingUtilities.invokeLater {
            mainWindow.statusBar.setUpdateHint("有更新：$latestVersion") {
                onUpdateClicked(mainWindow, info)
            }
        }
    }

    private fun selectAsset(release: GitHubReleaseApi.Release): GitHubReleaseApi.Asset? {
        val platform = currentPlatformKey()

        // 优先从 manifest.json 读取平台产物（发布工作流会生成）
        selectAssetFromManifest(release, platform)?.let { return it }

        // 兜底：按文件名/后缀推断
        return selectAssetByName(release.assets, platform)
    }

    private fun currentPlatformKey(): String {
        val os = SystemUtils.getOsName().lowercase(Locale.ROOT)
        return when {
            os.contains("mac") -> "macos"
            os.contains("win") -> "windows"
            else -> "linux"
        }
    }

    private data class LatestFromManifest(
        val version: String,
        val assetName: String,
        val downloadUrl: String,
        val releasePageUrl: String?,
    )

    private fun fetchLatestFromManifest(repo: String, platform: String): LatestFromManifest? {
        val url = "https://github.com/$repo/releases/latest/download/$MANIFEST_ASSET_NAME"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("User-Agent", "EditorX")
            .GET()
            .build()

        val resp = runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            .getOrNull()
            ?: return null

        if (resp.statusCode() !in 200..299) {
            logger.debug("读取 manifest 失败：status={}, url={}", resp.statusCode(), url)
            return null
        }

        val root = runCatching { json.parseToJsonElement(resp.body()) }
            .getOrNull()
            ?: return null

        val obj = root.safeObj() ?: return null
        val version = obj.str("version")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val tag = obj.str("tag")?.trim()?.takeIf { it.isNotEmpty() }

        val platforms = obj.obj("platforms") ?: return null
        val platformObj = platforms.obj(platform) ?: return null
        val artifactName = platformObj.str("artifact")?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val downloadUrl = if (tag != null) {
            "https://github.com/$repo/releases/download/$tag/$artifactName"
        } else {
            "https://github.com/$repo/releases/latest/download/$artifactName"
        }
        val releasePageUrl = if (tag != null) {
            "https://github.com/$repo/releases/tag/$tag"
        } else {
            "https://github.com/$repo/releases/latest"
        }

        return LatestFromManifest(
            version = version,
            assetName = artifactName,
            downloadUrl = downloadUrl,
            releasePageUrl = releasePageUrl,
        )
    }

    private fun selectAssetFromManifest(release: GitHubReleaseApi.Release, platform: String): GitHubReleaseApi.Asset? {
        val manifest = release.assets.firstOrNull { it.name == MANIFEST_ASSET_NAME } ?: return null
        val artifactName = fetchManifestArtifactName(manifest.browserDownloadUrl, platform) ?: return null
        return release.assets.firstOrNull { it.name == artifactName }
    }

    private fun fetchManifestArtifactName(downloadUrl: String, platform: String): String? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .header("Accept", "application/json")
            .header("User-Agent", "EditorX")
            .GET()
            .build()

        val resp = runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            .getOrNull()
            ?: return null

        if (resp.statusCode() !in 200..299) {
            logger.info("读取 manifest.json 失败：status={}", resp.statusCode())
            return null
        }

        val root = runCatching { json.parseToJsonElement(resp.body()) }
            .getOrNull()
            ?: return null

        val obj = root.safeObj() ?: return null
        val platforms = obj.obj("platforms") ?: return null
        val platformObj = platforms.obj(platform) ?: return null
        return platformObj.str("artifact")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun selectAssetByName(assets: List<GitHubReleaseApi.Asset>, platform: String): GitHubReleaseApi.Asset? {
        return when (platform) {
            "macos" -> {
                assets.firstOrNull { it.name.endsWith(".app.zip") }
                    ?: assets.firstOrNull { it.name.contains("mac", ignoreCase = true) && it.name.endsWith(".zip") }
            }
            "windows" -> {
                assets.firstOrNull { it.name.endsWith(".exe") }
                    ?: assets.firstOrNull { it.name.contains("win", ignoreCase = true) && it.name.endsWith(".exe") }
                    ?: assets.firstOrNull { it.name.contains("win", ignoreCase = true) && it.name.endsWith(".zip") }
            }
            else -> {
                assets.firstOrNull { it.name.contains("linux", ignoreCase = true) && it.name.endsWith(".zip") && !it.name.endsWith(".app.zip") }
                    ?: assets.firstOrNull { it.name.endsWith(".zip") && !it.name.endsWith(".app.zip") }
            }
        }
    }

    private fun onUpdateClicked(mainWindow: MainWindow, info: UpdateAvailable) {
        val confirm = UpdateDialog.confirmUpdate(mainWindow, info)
        if (!confirm) return
        downloadAndUpdate(mainWindow, info)
    }

    private fun downloadAndUpdate(mainWindow: MainWindow, info: UpdateAvailable) {
        val cancelled = AtomicBoolean(false)
        val dialog = UpdateDialog.showDownloadingDialog(mainWindow, "正在更新")
        dialog.setOnCancel { cancelled.set(true) }
        dialog.show()

        val handle = mainWindow.statusBar.beginProgressTask(
            message = "下载更新…",
            indeterminate = true,
            cancellable = true,
            onCancel = { cancelled.set(true) },
            maximum = 100
        )

        Thread {
            val packageFile = runCatching { downloadToCache(mainWindow, info, handle, dialog, cancelled) }.getOrNull()
            SwingUtilities.invokeLater {
                mainWindow.statusBar.endProgressTask(handle)
                dialog.close()
            }

            if (cancelled.get()) {
                logger.info("更新下载已取消")
                return@Thread
            }
            if (packageFile == null) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        mainWindow,
                        "下载更新失败，请稍后重试。",
                        "更新失败",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                return@Thread
            }

            if (!SystemUtils.isMacOS()) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        mainWindow,
                        "下载完成：${packageFile.toAbsolutePath()}\n当前平台暂不支持自动更新，请手动安装/替换。",
                        "更新",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    revealFile(packageFile)
                }
                return@Thread
            }

            val apply = SelfUpdater.applyDownloadedPackage(packageFile, ProcessHandle.current().pid())
            if (!apply.success) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        mainWindow,
                        apply.message,
                        "更新失败",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                return@Thread
            }

            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(
                    mainWindow,
                    "下载完成，正在更新并重启…",
                    "更新",
                    JOptionPane.INFORMATION_MESSAGE
                )
                // 退出让外部脚本接管替换
                System.exit(0)
            }
        }.apply {
            isDaemon = true
            name = "Updater"
            start()
        }
    }

    private fun revealFile(file: Path) {
        runCatching {
            val desktop = Desktop.getDesktop()
            val parent = file.toFile().parentFile ?: return
            desktop.open(parent)
        }.onFailure {
            logger.debug("打开下载目录失败: {}", file.toAbsolutePath(), it)
        }
    }

    private fun downloadToCache(
        mainWindow: MainWindow,
        info: UpdateAvailable,
        handle: editorx.gui.workbench.statusbar.StatusBar.ProgressHandle,
        dialog: UpdateDialog.DownloadingDialog,
        cancelled: AtomicBoolean
    ): Path? {
        val dir = VersionUtil.ensureUpdateCacheDir()
        val file = dir.resolve("EditorX-${info.latestVersion}-${info.assetName}")

        val request = HttpRequest.newBuilder()
            .uri(URI.create(info.downloadUrl))
            .header("User-Agent", "EditorX")
            .GET()
            .build()

        val resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (resp.statusCode() !in 200..299) {
            logger.info("下载更新返回非 2xx：status={}", resp.statusCode())
            return null
        }

        val total = resp.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull()
        val start = Instant.now()

        resp.body().use { input ->
            Files.newOutputStream(file).use { out ->
                copyWithProgress(input, out, total, cancelled) { done, percent ->
                    SwingUtilities.invokeLater {
                        if (percent == null) {
                            val msg = "下载更新… ${formatBytes(done)}"
                            mainWindow.statusBar.updateProgressTask(handle, msg, indeterminate = true)
                            dialog.update(msg, null)
                        } else {
                            val msg = "下载更新…"
                            mainWindow.statusBar.updateProgressTask(handle, percent, msg)
                            dialog.update("$msg ${formatBytes(done)}", percent)
                        }
                    }
                }
            }
        }

        if (cancelled.get()) {
            runCatching { Files.deleteIfExists(file) }
            return null
        }

        logger.info("更新包下载完成：{}（耗时={}s）", file, java.time.Duration.between(start, Instant.now()).seconds)
        return file
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        out: OutputStream,
        total: Long?,
        cancelled: AtomicBoolean,
        onProgress: (doneBytes: Long, percent: Int?) -> Unit
    ) {
        val buf = ByteArray(8192)
        var done = 0L
        var lastNotify = 0L
        while (true) {
            if (cancelled.get()) break
            val r = input.read(buf)
            if (r < 0) break
            out.write(buf, 0, r)
            done += r
            if (done - lastNotify > 128 * 1024) {
                lastNotify = done
                val percent = if (total != null && total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else null
                onProgress(done, percent)
            }
        }
        val percent = if (total != null && total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else null
        onProgress(done, percent)
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val b = bytes.toDouble()
        return when {
            b >= gb -> String.format("%.2f GB", b / gb)
            b >= mb -> String.format("%.2f MB", b / mb)
            b >= kb -> String.format("%.2f KB", b / kb)
            else -> "${bytes} B"
        }
    }

    private fun JsonElement.safeObj(): JsonObject? = (this as? JsonObject) ?: runCatching { this.jsonObject }.getOrNull()

    private fun JsonObject.str(key: String): String? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        val s = p.content
        if (s == "null") return null
        return s
    }

    private fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject) ?: (this[key]?.safeObj())
}
