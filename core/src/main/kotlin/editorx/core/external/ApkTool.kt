package editorx.core.external

import editorx.core.util.AppPaths
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 对 apktool 的封装，负责定位可执行文件并提供编译/反编译能力。
 */
object ApkTool {
    private val logger = LoggerFactory.getLogger(ApkTool::class.java)
    
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
    
    /**
     * 检查 apktool 是否可用（不执行实际命令）
     */
    fun isAvailable(): Boolean {
        return locateTool() != null
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
        logger.info("执行 apktool 命令: {}", command.joinToString(" "))
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        workingDir?.let { pb.directory(it) }
        val process = try {
            pb.start()
        } catch (e: Exception) {
            logger.error("启动 apktool 失败: {}", e.message, e)
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
            val candidates = listOf(
                File(home, "bin/java"),
                File(home, "bin/java.exe"),
                File(home, "bin/javaw.exe"),
            )
            candidates.firstOrNull { it.exists() && it.canExecute() }?.let {
                logger.debug("使用 Java: {}", it.absolutePath)
                return it.absolutePath
            }
        }
        logger.debug("使用系统 PATH 中的 java 命令")
        return "java"
    }

    private fun computeApktool(): Tool? {
        val appHome = AppPaths.appHome().toFile()
        logger.info("查找 apktool，appHome: {} (存在: {})", appHome.absolutePath, appHome.exists())

        // 1. 检查 toolchain/apktool
        locateExecutable(File(appHome, "toolchain/apktool"), "apktool")?.let {
            logger.info("找到 apktool: toolchain/apktool -> {}", it)
            return Tool.Executable(it)
        }

        // 2. 内置工具：优先使用 apktool.jar
        val bundledJar = File(appHome, "tools/apktool.jar")
        logger.debug("检查 apktool.jar: {} (存在: {}, 是文件: {})", 
            bundledJar.absolutePath, bundledJar.exists(), bundledJar.isFile)
        if (bundledJar.exists() && bundledJar.isFile) {
            logger.info("找到 apktool: tools/apktool.jar (路径: {})", bundledJar.absolutePath)
            return Tool.Jar(bundledJar.absolutePath)
        } else {
            logger.warn("未找到 tools/apktool.jar (路径: {}, 存在: {}, 是文件: {})", 
                bundledJar.absolutePath, bundledJar.exists(), bundledJar.isFile)
        }
        
        // 开发模式：如果appHome不是项目根目录，也检查项目根目录
        val userDir = File(System.getProperty("user.dir", "."))
        // 尝试找到项目根目录：从 user.dir 向上查找，直到找到包含 tools/ 或 plugins/ 的目录
        var projectRoot: File? = null
        var current = userDir
        while (current != null && current.exists()) {
            val toolsDir = File(current, "tools")
            val pluginsDir = File(current, "plugins")
            if (toolsDir.exists() || pluginsDir.exists()) {
                projectRoot = current
                break
            }
            val parent = current.parentFile
            if (parent == null || parent == current) break
            current = parent
        }
        
        // 如果找到了项目根目录，且与 appHome 不同，检查项目根目录的 apktool
        if (projectRoot != null && projectRoot.absolutePath != appHome.absolutePath) {
            val devToolsJar = File(projectRoot, "tools/apktool.jar")
            logger.info("开发模式：检查项目根目录的 apktool.jar: {} (存在: {}, 是文件: {})", 
                devToolsJar.absolutePath, devToolsJar.exists(), devToolsJar.isFile)
            if (devToolsJar.exists() && devToolsJar.isFile) {
                logger.info("找到 apktool: 项目根目录的 tools/apktool.jar (路径: {})", devToolsJar.absolutePath)
                return Tool.Jar(devToolsJar.absolutePath)
            }
            // 也检查项目根目录的 tools/apktool 脚本
            val devToolsScript = File(projectRoot, "tools/apktool")
            logger.info("开发模式：检查项目根目录的 apktool 脚本: {} (存在: {}, 可执行: {})", 
                devToolsScript.absolutePath, devToolsScript.exists(), devToolsScript.canExecute())
            if (devToolsScript.exists() && ensureExecutable(devToolsScript)) {
                logger.info("找到 apktool: 项目根目录的 tools/apktool (路径: {})", devToolsScript.absolutePath)
                return Tool.Executable(devToolsScript.absolutePath)
            }
        }

        // 3. 检查系统 PATH 中的 apktool
        try {
            val process = ProcessBuilder("apktool", "--version").start()
            if (process.waitFor() == 0) {
                logger.info("找到 apktool: 系统 PATH")
                return Tool.Executable("apktool")
            }
        } catch (e: Exception) {
            logger.debug("系统 PATH 中未找到 apktool: {}", e.message)
        }

        // 4. 检查 legacy tools/apktool 脚本
        val legacy = File(appHome, "tools/apktool")
        if (legacy.exists() && ensureExecutable(legacy)) {
            logger.info("找到 apktool: tools/apktool (legacy, 路径: {})", legacy.absolutePath)
            return Tool.Executable(legacy.absolutePath)
        } else {
            logger.warn("未找到 tools/apktool (路径: {}, 存在: {}, 可执行: {})", 
                legacy.absolutePath, legacy.exists(), legacy.canExecute())
        }

        // 5. 检查常见系统路径
        val commonPaths = listOf(
            "/usr/local/bin/apktool",
            "/opt/homebrew/bin/apktool",
            "/usr/bin/apktool",
            System.getProperty("user.home") + "/.local/bin/apktool"
        )
        for (path in commonPaths) {
            val candidate = File(path)
            if (candidate.exists() && ensureExecutable(candidate)) {
                logger.info("找到 apktool: {}", path)
                return Tool.Executable(candidate.absolutePath)
            }
        }
        
        logger.error("未找到 apktool，已检查的路径: appHome={}, toolchain={}, toolsJar={}, toolsScript={}, PATH, commonPaths={}",
            appHome.absolutePath,
            File(appHome, "toolchain/apktool").absolutePath,
            File(appHome, "tools/apktool.jar").absolutePath,
            File(appHome, "tools/apktool").absolutePath,
            commonPaths.joinToString()
        )
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
