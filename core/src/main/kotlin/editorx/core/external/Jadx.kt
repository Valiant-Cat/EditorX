package editorx.core.external

import editorx.core.util.AppPaths
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import java.io.File

/**
 * 对 JADX 的封装：负责定位可用实现并提供 Java 源码反编译能力。
 *
 * 优先使用内置 `jadx-core`（开箱即用，不依赖外部 jadx 环境），仅在内置不可用时回退到外部命令行。
 */
object Jadx {
    enum class Status { SUCCESS, FAILED, NOT_FOUND, CANCELLED }

    data class RunResult(
        val status: Status,
        val exitCode: Int,
        val output: String,
        /** Jadx 输出中的错误数量（若可解析）。 */
        val errorCount: Int? = null,
    )

    private val externalErrorCountRegex = Regex("""finished\s+with\s+errors,\s*count:\s*(\d+)""", RegexOption.IGNORE_CASE)

    @Volatile
    private var cachedLocate: String? = null

    fun locate(): String? {
        cachedLocate?.let { return it }
        val resolved = if (isEmbeddedReady()) "embedded" else computeExternalJadxPath()
        cachedLocate = resolved
        return resolved
    }

    fun decompile(
        inputFile: File,
        outputDir: File,
        cancelSignal: (() -> Boolean)? = null,
    ): RunResult {
        if (cancelSignal?.invoke() == true) return RunResult(Status.CANCELLED, -1, "cancelled")

        val embedded = isEmbeddedReady()
        val raw = if (embedded) {
            decompileEmbedded(inputFile, outputDir, cancelSignal)
        } else {
            decompileExternal(inputFile, outputDir, cancelSignal)
        }

        // 将输出写入到输出目录，便于排查（仅当目录已存在）。
        writeLogIfPossible(outputDir, raw.output)
        return raw
    }

    private fun isEmbeddedReady(): Boolean {
        // DexInputPlugin 由 `jadx-dex-input` 提供（运行时依赖）；若缺失则认为内置不可用。
        return runCatching { Class.forName("jadx.plugins.input.dex.DexInputPlugin") }.isSuccess
    }

    private fun decompileEmbedded(
        inputFile: File,
        outputDir: File,
        cancelSignal: (() -> Boolean)?,
    ): RunResult {
        if (cancelSignal?.invoke() == true) return RunResult(Status.CANCELLED, -1, "cancelled")
        runCatching { outputDir.mkdirs() }

        val srcDir = File(outputDir, "sources")
        val resDir = File(outputDir, "resources")

        val args = JadxArgs().apply {
            setInputFile(inputFile)
            setOutDir(outputDir)
            setOutDirSrc(srcDir)
            setOutDirRes(resDir)
            setSkipResources(true) // EditorX 当前只读取 sources/ 下的 java
            setThreadsCount(defaultThreadsCount())
        }

        return try {
            JadxDecompiler(args).use { decompiler ->
                decompiler.load()

                if (cancelSignal?.invoke() == true) {
                    return RunResult(Status.CANCELLED, -1, "cancelled")
                }

                val taskExecutor = decompiler.saveTaskExecutor
                val listener = JadxDecompiler.ProgressListener { _, _ ->
                    if (cancelSignal?.invoke() == true) {
                        runCatching { taskExecutor.terminate() }
                    }
                }

                decompiler.save(args.threadsCount, listener)

                val errorCount = decompiler.errorsCount
                val warnCount = decompiler.warnsCount
                val hasUsableOutput = hasUsableOutput(outputDir)
                val cancelled = cancelSignal?.invoke() == true
                val status = when {
                    cancelled -> Status.CANCELLED
                    errorCount > 0 && !hasUsableOutput -> Status.FAILED
                    else -> Status.SUCCESS
                }
                val exitCode = when (status) {
                    Status.SUCCESS -> 0
                    Status.CANCELLED -> -1
                    else -> 1
                }
                RunResult(
                    status = status,
                    exitCode = exitCode,
                    output = "jadx (embedded) finished. errors=$errorCount, warns=$warnCount",
                    errorCount = errorCount,
                )
            }
        } catch (e: Throwable) {
            val cancelled = cancelSignal?.invoke() == true
            val status = if (cancelled) Status.CANCELLED else Status.FAILED
            RunResult(status, -1, e.message ?: "jadx (embedded) failed")
        }
    }

    private fun defaultThreadsCount(): Int {
        val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        // 反编译大多是 IO + CPU 混合任务，保守使用少量线程避免 UI 卡顿/线程过多。
        return cpu.coerceAtMost(4)
    }

    private fun decompileExternal(
        inputFile: File,
        outputDir: File,
        cancelSignal: (() -> Boolean)?,
    ): RunResult {
        val executable = computeExternalJadxPath() ?: return RunResult(Status.NOT_FOUND, -1, "jadx not found")
        val command = listOf(
            executable,
            "-d",
            outputDir.absolutePath,
            inputFile.absolutePath,
        )
        val raw = runExternal(command, inputFile.parentFile, cancelSignal)
        val errorCount = parseExternalErrorCount(raw.output)
        val hasUsableOutput = hasUsableOutput(outputDir)
        val finalStatus =
            when (raw.status) {
                Status.FAILED -> if (hasUsableOutput) Status.SUCCESS else Status.FAILED
                else -> raw.status
            }
        return RunResult(
            status = finalStatus,
            exitCode = raw.exitCode,
            output = raw.output,
            errorCount = errorCount,
        )
    }

    private fun runExternal(command: List<String>, workingDir: File?, cancelSignal: (() -> Boolean)?): RunResult {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        workingDir?.let { pb.directory(it) }
        val process = try {
            pb.start()
        } catch (e: Exception) {
            return RunResult(Status.FAILED, -1, e.message ?: "failed to start jadx")
        }

        while (true) {
            if (cancelSignal?.invoke() == true) {
                process.destroy()
                if (process.isAlive) process.destroyForcibly()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                return RunResult(Status.CANCELLED, -1, output)
            }
            try {
                val exitCode = process.exitValue()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val status = if (exitCode == 0) Status.SUCCESS else Status.FAILED
                return RunResult(status, exitCode, output)
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(100)
            }
        }
    }

    private fun computeExternalJadxPath(): String? {
        val appHome = AppPaths.appHome().toFile()

        locateExecutable(File(appHome, "toolchain/jadx"), "jadx")?.let { return it }

        val legacy = File(appHome, "tools/jadx")
        if (legacy.exists() && ensureExecutable(legacy)) {
            return legacy.absolutePath
        }

        try {
            val process = ProcessBuilder("jadx", "--version").start()
            if (process.waitFor() == 0) return "jadx"
        } catch (_: Exception) {
        }

        val commonPaths = listOf(
            "/usr/local/bin/jadx",
            "/opt/homebrew/bin/jadx",
            "/usr/bin/jadx",
            System.getProperty("user.home") + "/.local/bin/jadx",
        )
        for (path in commonPaths) {
            val candidate = File(path)
            if (candidate.exists() && ensureExecutable(candidate)) {
                return candidate.absolutePath
            }
        }
        return null
    }

    private fun locateExecutable(dir: File, baseName: String): String? {
        if (!dir.exists()) return null
        val candidates = listOf(
            File(dir, baseName),
            File(dir, "$baseName.sh"),
            File(dir, "$baseName.bat"),
            File(dir, "$baseName.cmd"),
        )
        for (candidate in candidates) {
            if (candidate.exists() && ensureExecutable(candidate)) {
                return candidate.absolutePath
            }
        }
        return null
    }

    private fun ensureExecutable(file: File): Boolean {
        if (!file.exists()) return false
        if (file.canExecute()) return true
        return file.setExecutable(true)
    }

    private fun parseExternalErrorCount(output: String): Int? {
        val m = externalErrorCountRegex.find(output) ?: return null
        return m.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun hasUsableOutput(outputDir: File): Boolean {
        if (!outputDir.exists() || !outputDir.isDirectory) return false
        val sources = File(outputDir, "sources")
        val resources = File(outputDir, "resources")
        if (sources.isDirectory || resources.isDirectory) return true
        return runCatching { outputDir.listFiles()?.isNotEmpty() == true }.getOrDefault(false)
    }

    private fun writeLogIfPossible(outputDir: File, output: String) {
        if (!outputDir.exists() || !outputDir.isDirectory) return
        if (output.isBlank()) return
        runCatching {
            File(outputDir, "jadx.log").writeText(output)
        }
    }
}
