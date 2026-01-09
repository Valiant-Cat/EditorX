package editorx.core.external

import editorx.core.util.AppPaths
import java.io.File

/**
 * 对 apktool 的封装，负责定位可执行文件并提供编译/反编译能力。
 */
object ApkTool {
    enum class Status { SUCCESS, FAILED, NOT_FOUND, CANCELLED }

    data class RunResult(val status: Status, val exitCode: Int, val output: String)

    private sealed interface Tool {
        data class Executable(val path: String) : Tool
        data class Jar(val jarPath: String) : Tool
    }

    @Volatile
    private var cachedTool: Tool? = null

    private fun locateTool(): Tool? {
        cachedTool?.let { return it }
        val resolved = computeApktool()
        cachedTool = resolved
        return resolved
    }

    fun build(workspaceRoot: File, outputApk: File, cancelSignal: (() -> Boolean)? = null): RunResult {
        val tool = locateTool() ?: return RunResult(Status.NOT_FOUND, -1, "apktool not found")
        val command = tool.commandPrefix() + listOf("b", workspaceRoot.absolutePath, "-o", outputApk.absolutePath)
        return run(command, workspaceRoot, cancelSignal)
    }

    fun decompile(
        apkFile: File,
        outputDir: File,
        force: Boolean = true,
        cancelSignal: (() -> Boolean)? = null
    ): RunResult {
        val tool = locateTool() ?: return RunResult(Status.NOT_FOUND, -1, "apktool not found")
        val command = (tool.commandPrefix() + listOf(
            "d",
            apkFile.absolutePath,
            "-o",
            outputDir.absolutePath
        )).toMutableList()
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

    private fun Tool.commandPrefix(): List<String> {
        return when (this) {
            is Tool.Executable -> listOf(path)
            is Tool.Jar -> listOf(javaBin(), "-jar", jarPath)
        }
    }

    private fun javaBin(): String {
        val home = System.getProperty("java.home").orEmpty()
        if (home.isNotBlank()) {
            val candidate = File(home, "bin/java")
            if (candidate.exists() && candidate.canExecute()) return candidate.absolutePath
        }
        return "java"
    }

    private fun computeApktool(): Tool? {
        val appHome = AppPaths.appHome().toFile()

        locateExecutable(File(appHome, "toolchain/apktool"), "apktool")?.let { return Tool.Executable(it) }

        // 内置工具：优先使用 apktool.jar，避免依赖系统 java 命令
        val bundledJar = File(appHome, "tools/apktool.jar")
        if (bundledJar.exists() && bundledJar.isFile) return Tool.Jar(bundledJar.absolutePath)

        try {
            val process = ProcessBuilder("apktool", "--version").start()
            if (process.waitFor() == 0) {
                return Tool.Executable("apktool")
            }
        } catch (_: Exception) {
        }

        val legacy = File(appHome, "tools/apktool")
        if (legacy.exists() && ensureExecutable(legacy)) {
            return Tool.Executable(legacy.absolutePath)
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
                return Tool.Executable(candidate.absolutePath)
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
