package editorx.core.external

import editorx.core.util.AppPaths
import java.io.File

/**
 * 对 D8 的封装：负责将 JAR 转换为 DEX。
 */
object D8 {
    enum class Status { SUCCESS, FAILED, NOT_FOUND, CANCELLED }

    data class RunResult(val status: Status, val exitCode: Int, val output: String)

    @Volatile
    private var cachedTool: Tool? = null

    private sealed interface Tool {
        data class Executable(val path: String) : Tool
        data class Jar(val jarPath: String) : Tool
    }

    fun locate(): String? {
        cachedTool?.let { return toolName(it) }
        val resolved = computeD8Tool()
        cachedTool = resolved
        return resolved?.let { toolName(it) }
    }

    fun dexJars(
        inputJars: List<File>,
        outputDir: File,
        libs: List<File> = emptyList(),
        cancelSignal: (() -> Boolean)? = null
    ): RunResult {
        val tool = cachedTool ?: computeD8Tool()
        if (tool == null) return RunResult(Status.NOT_FOUND, -1, "d8 not found")
        cachedTool = tool
        if (cancelSignal?.invoke() == true) return RunResult(Status.CANCELLED, -1, "cancelled")

        outputDir.mkdirs()
        val command = when (tool) {
            is Tool.Executable -> mutableListOf(tool.path, "--output", outputDir.absolutePath)
            is Tool.Jar -> mutableListOf(
                javaBin(),
                "-cp",
                tool.jarPath,
                "com.android.tools.r8.D8",
                "--output",
                outputDir.absolutePath
            )
        }
        libs.filter { it.exists() }.forEach { lib ->
            command.add("--lib")
            command.add(lib.absolutePath)
        }
        inputJars.filter { it.exists() }.forEach { command.add(it.absolutePath) }
        return run(command, outputDir, cancelSignal)
    }

    private fun run(command: List<String>, workingDir: File?, cancelSignal: (() -> Boolean)?): RunResult {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        workingDir?.let { pb.directory(it) }
        val process = try {
            pb.start()
        } catch (e: Exception) {
            return RunResult(Status.FAILED, -1, e.message ?: "failed to start d8")
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

    private fun computeD8Tool(): Tool? {
        val appHome = AppPaths.appHome().toFile()

        locateExecutable(File(appHome, "toolchain/d8"), "d8")?.let { return Tool.Executable(it) }

        val bundledJar = File(appHome, "tools/d8.jar")
        if (bundledJar.exists() && bundledJar.isFile) return Tool.Jar(bundledJar.absolutePath)

        val legacy = File(appHome, "tools/d8")
        if (legacy.exists() && ensureExecutable(legacy)) {
            return Tool.Executable(legacy.absolutePath)
        }

        findFromAndroidSdk()?.let { return Tool.Executable(it) }

        try {
            val process = ProcessBuilder("d8", "--version").start()
            if (process.waitFor() == 0) return Tool.Executable("d8")
        } catch (_: Exception) {
        }

        val commonPaths = listOf(
            "/usr/local/bin/d8",
            "/opt/homebrew/bin/d8",
            "/usr/bin/d8",
            System.getProperty("user.home") + "/.local/bin/d8"
        )
        for (path in commonPaths) {
            val candidate = File(path)
            if (candidate.exists() && ensureExecutable(candidate)) {
                return Tool.Executable(candidate.absolutePath)
            }
        }
        return null
    }

    private fun findFromAndroidSdk(): String? {
        val sdkHome = resolveAndroidSdkHome() ?: return null
        val buildToolsDir = File(sdkHome, "build-tools")
        if (!buildToolsDir.isDirectory) return null

        val versions = buildToolsDir.listFiles()?.filter { it.isDirectory } ?: return null
        val sorted = versions.sortedWith { a, b ->
            compareVersion(b.name, a.name)
        }
        for (dir in sorted) {
            val candidate = locateExecutable(dir, "d8")
            if (candidate != null) return candidate
        }
        return null
    }

    private fun resolveAndroidSdkHome(): String? {
        val env = System.getenv("ANDROID_HOME")?.trim().orEmpty()
            .ifEmpty { System.getenv("ANDROID_SDK_ROOT")?.trim().orEmpty() }
        if (env.isNotEmpty()) return env

        val osName = System.getProperty("os.name")?.lowercase().orEmpty()
        val userHome = System.getProperty("user.home")?.trim().orEmpty()
        if (userHome.isEmpty()) return null

        val candidates = mutableListOf<String>()
        when {
            osName.contains("mac") -> candidates += "$userHome/Library/Android/sdk"
            osName.contains("windows") -> {
                val localAppData = System.getenv("LOCALAPPDATA")?.trim().orEmpty()
                if (localAppData.isNotEmpty()) candidates += "$localAppData/Android/Sdk"
                candidates += "$userHome/AppData/Local/Android/Sdk"
            }
            else -> candidates += "$userHome/Android/Sdk"
        }

        return candidates.firstOrNull { File(it).isDirectory }
    }

    fun resolveAndroidJar(): File? {
        val sdkHome = resolveAndroidSdkHome() ?: return null
        val platformsDir = File(sdkHome, "platforms")
        if (!platformsDir.isDirectory) return null

        val platforms = platformsDir.listFiles()?.filter { it.isDirectory } ?: return null
        val sorted = platforms.sortedWith { a, b ->
            compareVersion(b.name.removePrefix("android-"), a.name.removePrefix("android-"))
        }
        for (dir in sorted) {
            val jar = File(dir, "android.jar")
            if (jar.isFile) return jar
        }
        return null
    }

    private fun compareVersion(a: String, b: String): Int {
        val aParts = a.split('.').mapNotNull { it.toIntOrNull() }
        val bParts = b.split('.').mapNotNull { it.toIntOrNull() }
        val max = maxOf(aParts.size, bParts.size)
        for (i in 0 until max) {
            val av = aParts.getOrNull(i) ?: 0
            val bv = bParts.getOrNull(i) ?: 0
            if (av != bv) return av - bv
        }
        return 0
    }

    private fun locateExecutable(dir: File, baseName: String): String? {
        if (!dir.exists()) return null
        val candidates = listOf(
            File(dir, baseName),
            File(dir, "$baseName.bat"),
            File(dir, "$baseName.cmd"),
            File(dir, "$baseName.exe")
        )
        for (candidate in candidates) {
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

    private fun toolName(tool: Tool): String {
        return when (tool) {
            is Tool.Executable -> tool.path
            is Tool.Jar -> tool.jarPath
        }
    }

    private fun ensureExecutable(file: File): Boolean {
        if (!file.exists()) return false
        if (file.canExecute()) return true
        return file.setExecutable(true)
    }
}
