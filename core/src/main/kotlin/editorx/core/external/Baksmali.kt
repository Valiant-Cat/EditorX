package editorx.core.external

import editorx.core.util.AppPaths
import java.io.File

/**
 * 对 baksmali 的封装：负责将 DEX 反编译为 smali 目录。
 */
object Baksmali {
    enum class Status { SUCCESS, FAILED, NOT_FOUND, CANCELLED }

    data class RunResult(val status: Status, val exitCode: Int, val output: String)

    @Volatile
    private var cachedPath: String? = null

    fun locate(): String? {
        cachedPath?.let { return it }
        val resolved = computeBaksmaliPath()
        cachedPath = resolved
        return resolved
    }

    fun disassemble(
        dexFile: File,
        outputDir: File,
        cancelSignal: (() -> Boolean)? = null
    ): RunResult {
        val toolPath = locate() ?: return RunResult(Status.NOT_FOUND, -1, "baksmali not found")
        if (cancelSignal?.invoke() == true) return RunResult(Status.CANCELLED, -1, "cancelled")

        outputDir.mkdirs()
        val command = if (toolPath.endsWith(".jar", ignoreCase = true)) {
            val classpath = buildClasspath(toolPath)
            listOf(
                javaBin(),
                "-cp",
                classpath,
                "org.jf.baksmali.Main",
                "disassemble",
                "-o",
                outputDir.absolutePath,
                dexFile.absolutePath
            )
        } else {
            listOf(
                toolPath,
                "disassemble",
                "-o",
                outputDir.absolutePath,
                dexFile.absolutePath
            )
        }
        return run(command, dexFile.parentFile, cancelSignal)
    }

    private fun buildClasspath(baksmaliJar: String): String {
        val jarFile = File(baksmaliJar)
        val jarDir = jarFile.parentFile
        val libDir = File(jarDir, "lib")
        val libJars = if (libDir.exists() && libDir.isDirectory) {
            libDir.listFiles { _, name -> name.endsWith(".jar") }?.map { it.absolutePath } ?: emptyList()
        } else {
            emptyList()
        }
        return (listOf(baksmaliJar) + libJars).joinToString(File.pathSeparator)
    }

    private fun run(command: List<String>, workingDir: File?, cancelSignal: (() -> Boolean)?): RunResult {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        workingDir?.let { pb.directory(it) }
        val process = try {
            pb.start()
        } catch (e: Exception) {
            return RunResult(Status.FAILED, -1, e.message ?: "failed to start baksmali")
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

    private fun computeBaksmaliPath(): String? {
        val appHome = AppPaths.appHome().toFile()

        locateExecutable(File(appHome, "toolchain/baksmali"), "baksmali")?.let { return it }

        val bundledJar = File(appHome, "tools/baksmali.jar")
        if (bundledJar.exists() && bundledJar.isFile) return bundledJar.absolutePath

        val legacy = File(appHome, "tools/baksmali")
        if (legacy.exists() && ensureExecutable(legacy)) {
            return legacy.absolutePath
        }

        try {
            val process = ProcessBuilder("baksmali", "--version").start()
            if (process.waitFor() == 0) return "baksmali"
        } catch (_: Exception) {
        }

        val commonPaths = listOf(
            "/usr/local/bin/baksmali",
            "/opt/homebrew/bin/baksmali",
            "/usr/bin/baksmali",
            System.getProperty("user.home") + "/.local/bin/baksmali"
        )
        for (path in commonPaths) {
            val candidate = File(path)
            if (candidate.exists() && ensureExecutable(candidate)) {
                return candidate.absolutePath
            }
        }
        return null
    }

    private fun javaBin(): String {
        val home = System.getProperty("java.home").orEmpty()
        if (home.isNotBlank()) {
            val candidates = listOf(
                File(home, "bin/java"),
                File(home, "bin/java.exe"),
                File(home, "bin/javaw.exe")
            )
            candidates.firstOrNull { it.exists() && it.canExecute() }?.let { return it.absolutePath }
        }
        return "java"
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
