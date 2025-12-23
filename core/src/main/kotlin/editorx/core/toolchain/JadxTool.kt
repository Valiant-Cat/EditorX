package editorx.core.toolchain

import java.io.File

/**
 * 对 JADX CLI 的封装：负责定位可执行文件并提供 Java 源码反编译能力。
 *
 * 说明：
 * - 当前实现基于命令行工具（jadx），未嵌入 jadx-core。
 * - 若机器未安装 jadx，可将可执行文件放到项目 `toolchain/jadx/` 或 `tools/jadx` 下。
 */
object JadxTool {
    enum class Status { SUCCESS, FAILED, NOT_FOUND, CANCELLED }

    data class RunResult(val status: Status, val exitCode: Int, val output: String)

    @Volatile
    private var cachedPath: String? = null

    fun locate(): String? {
        cachedPath?.let { return it }
        val resolved = computeJadxPath()
        cachedPath = resolved
        return resolved
    }

    fun decompile(
        inputFile: File,
        outputDir: File,
        cancelSignal: (() -> Boolean)? = null,
    ): RunResult {
        val executable = locate() ?: return RunResult(Status.NOT_FOUND, -1, "jadx not found")
        val command = listOf(
            executable,
            "-d",
            outputDir.absolutePath,
            inputFile.absolutePath,
        )
        return run(command, inputFile.parentFile, cancelSignal)
    }

    private fun run(command: List<String>, workingDir: File?, cancelSignal: (() -> Boolean)?): RunResult {
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

    private fun computeJadxPath(): String? {
        val projectRoot = File(System.getProperty("user.dir"))

        locateExecutable(File(projectRoot, "toolchain/jadx"), "jadx")?.let { return it }

        val legacy = File(projectRoot, "tools/jadx")
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
}

