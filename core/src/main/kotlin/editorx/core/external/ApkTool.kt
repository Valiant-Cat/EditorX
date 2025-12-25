package editorx.core.external

import java.io.File

/**
 * 对 apktool 的封装，负责定位可执行文件并提供编译/反编译能力。
 */
object ApkTool {
    enum class Status { SUCCESS, FAILED, NOT_FOUND, CANCELLED }

    data class RunResult(val status: Status, val exitCode: Int, val output: String)

    @Volatile
    private var cachedPath: String? = null

    fun locate(): String? {
        cachedPath?.let { return it }
        val resolved = computeApktoolPath()
        cachedPath = resolved
        return resolved
    }

    fun build(workspaceRoot: File, outputApk: File, cancelSignal: (() -> Boolean)? = null): RunResult {
        val executable = locate() ?: return RunResult(Status.NOT_FOUND, -1, "apktool not found")
        val command = listOf(executable, "b", workspaceRoot.absolutePath, "-o", outputApk.absolutePath)
        return run(command, workspaceRoot, cancelSignal)
    }

    fun decompile(
        apkFile: File,
        outputDir: File,
        force: Boolean = true,
        cancelSignal: (() -> Boolean)? = null
    ): RunResult {
        val executable = locate() ?: return RunResult(Status.NOT_FOUND, -1, "apktool not found")
        val command = mutableListOf(
            executable,
            "d",
            apkFile.absolutePath,
            "-o",
            outputDir.absolutePath
        )
        if (force) command += "-f"
        val workingDir = apkFile.parentFile
        return run(command, workingDir, cancelSignal)
    }

    private fun run(command: List<String>, workingDir: File?, cancelSignal: (() -> Boolean)?): RunResult {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        workingDir?.let { pb.directory(it) }
        val process = try {
            pb.start()
        } catch (e: Exception) {
            return RunResult(Status.FAILED, -1, e.message ?: "failed to start apktool")
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

    private fun computeApktoolPath(): String? {
        val projectRoot = File(System.getProperty("user.dir"))

        locateExecutable(File(projectRoot, "toolchain/apktool"), "apktool")?.let { return it }

        val legacy = File(projectRoot, "tools/apktool")
        if (legacy.exists() && ensureExecutable(legacy)) {
            return legacy.absolutePath
        }

        try {
            val process = ProcessBuilder("apktool", "--version").start()
            if (process.waitFor() == 0) {
                return "apktool"
            }
        } catch (_: Exception) {
        }

        val commonPaths = listOf(
            "/usr/local/bin/apktool",
            "/opt/homebrew/bin/apktool",
            "/usr/bin/apktool",
            System.getProperty("user.home") + "/.local/bin/apktool"
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
            File(dir, "$baseName.cmd")
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
