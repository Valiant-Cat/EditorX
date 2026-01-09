package editorx.core.external

import java.io.File
import java.nio.file.Files
import java.nio.charset.StandardCharsets

/**
 * 对 smali 工具的封装：负责定位可执行文件并提供 smali 文件编译能力。
 * 用于将单个 smali 文件编译成 DEX，然后可以用 jadx 反编译为 Java。
 */
object Smali {
    enum class Status { SUCCESS, FAILED, NOT_FOUND, CANCELLED }

    data class RunResult(val status: Status, val exitCode: Int, val output: String)

    @Volatile
    private var cachedPath: String? = null

    fun locate(): String? {
        cachedPath?.let { return it }
        val resolved = computeSmaliPath()
        cachedPath = resolved
        return resolved
    }

    /**
     * 将单个 smali 文件编译成 DEX 文件
     * 注意：smali 工具需要输入目录，所以我们需要创建一个临时目录结构
     * @param smaliFile 要编译的 smali 文件
     * @param outputDexFile 输出的 DEX 文件
     * @param cancelSignal 取消信号
     */
    fun assemble(
        smaliFile: File,
        outputDexFile: File,
        smaliText: String? = null,
        cancelSignal: (() -> Boolean)? = null
    ): RunResult {
        val smaliPath = locate() ?: return RunResult(Status.NOT_FOUND, -1, "smali not found")

        // smali 工具需要输入目录，而不是单个文件
        // 我们需要创建一个临时目录，保持原有的目录结构
        val tempInputDir = Files.createTempDirectory("smali_input_").toFile()
        tempInputDir.deleteOnExit()

        try {
            // 获取 smali 文件相对于其父目录的路径
            // 例如：.../smali/a/b.smali -> 需要创建 tempDir/smali/a/b.smali
            val smaliParent = smaliFile.parentFile
            val smaliDirName = smaliParent.name // 通常是 "smali" 或 "smali_classes2"

            // 创建对应的目录结构
            val targetDir = File(tempInputDir, smaliDirName)
            val relativePath = smaliFile.relativeTo(smaliParent.parentFile ?: smaliParent)
            val targetFile = File(tempInputDir, relativePath.path)
            targetFile.parentFile.mkdirs()

            // 将 smali 文件复制/写入到临时目录（支持未保存的编辑内容）
            if (smaliText != null) {
                Files.writeString(targetFile.toPath(), smaliText, StandardCharsets.UTF_8)
            } else {
                Files.copy(smaliFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }

            // 构建命令：smali assemble -o output.dex inputDir
            // smali.jar 没有主清单属性，需要使用主类运行
            // 如果 smali.jar 缺少依赖，尝试查找 lib 目录中的依赖
            val command = if (smaliPath.endsWith(".jar")) {
                val jarFile = File(smaliPath)
                val jarDir = jarFile.parentFile
                val libDir = File(jarDir, "lib")

                val logger = org.slf4j.LoggerFactory.getLogger(Smali::class.java)
                logger.info("smali.jar 路径: $smaliPath")
                logger.info("jar 目录: ${jarDir.absolutePath}")
                logger.info("lib 目录: ${libDir.absolutePath}, 存在: ${libDir.exists()}, 是目录: ${libDir.isDirectory}")

                // 构建 classpath：包含 smali.jar 和 lib 目录下的所有 jar
                val classpath = if (libDir.exists() && libDir.isDirectory) {
                    val libJars =
                        libDir.listFiles { _, name -> name.endsWith(".jar") }?.map { it.absolutePath } ?: emptyList()
                    logger.info("找到 ${libJars.size} 个依赖 jar: ${libJars.joinToString(", ")}")
                    if (libJars.isNotEmpty()) {
                        val cp = (listOf(smaliPath) + libJars).joinToString(File.pathSeparator)
                        logger.info("构建的 classpath: $cp")
                        cp
                    } else {
                        logger.warn("lib 目录存在但没有找到 jar 文件")
                        smaliPath
                    }
                } else {
                    logger.warn("lib 目录不存在，只使用 smali.jar")
                    smaliPath
                }

                System.out.println("=== SMALI 工具信息 ===")
                System.out.println("smali.jar: $smaliPath")
                System.out.println("lib 目录: ${libDir.absolutePath} (存在: ${libDir.exists()})")
                if (libDir.exists()) {
                    val libJars = libDir.listFiles { _, name -> name.endsWith(".jar") }?.map { it.name } ?: emptyList()
                    System.out.println("依赖 jar: ${libJars.joinToString(", ")}")
                }
                System.out.println("classpath: $classpath")
                System.out.println("====================")

                listOf(
                    "java",
                    "-cp",
                    classpath,
                    "org.jf.smali.Main",
                    "assemble",
                    "-o",
                    outputDexFile.absolutePath,
                    tempInputDir.absolutePath
                )
            } else {
                listOf(
                    smaliPath,
                    "assemble",
                    "-o",
                    outputDexFile.absolutePath,
                    tempInputDir.absolutePath
                )
            }

            val result = run(command, tempInputDir, cancelSignal)

            // 清理临时目录
            tempInputDir.deleteRecursively()

            return result

        } catch (e: Exception) {
            tempInputDir.deleteRecursively()
            return RunResult(Status.FAILED, -1, "Failed to setup smali input: ${e.message}")
        }
    }

    private fun run(command: List<String>, workingDir: File?, cancelSignal: (() -> Boolean)?): RunResult {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        workingDir?.let { pb.directory(it) }
        val process = try {
            pb.start()
        } catch (e: Exception) {
            return RunResult(Status.FAILED, -1, e.message ?: "failed to start smali")
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

    private fun computeSmaliPath(): String? {
        val projectRoot = File(System.getProperty("user.dir"))

        // 优先查找项目内置的工具
        // 1. 查找 tools/smali 包装脚本（类似 apktool）
        val toolsDir = File(projectRoot, "tools")
        val smaliScript = File(toolsDir, "smali")
        if (smaliScript.exists() && ensureExecutable(smaliScript)) {
            return smaliScript.absolutePath
        }

        // 2. 查找 tools/smali.jar（JAR 文件，需要通过 java -jar 运行）
        val smaliJar = File(toolsDir, "smali.jar")
        if (smaliJar.exists() && smaliJar.isFile) {
            // 返回 JAR 路径，会在 assemble 函数中处理
            return smaliJar.absolutePath
        }

        // 3. 查找 toolchain/smali 可执行文件
        locateExecutable(File(projectRoot, "toolchain/smali"), "smali")?.let { return it }

        try {
            val process = ProcessBuilder("smali", "--version").start()
            if (process.waitFor() == 0) return "smali"
        } catch (_: Exception) {
        }

        val commonPaths = listOf(
            "/usr/local/bin/smali",
            "/opt/homebrew/bin/smali",
            "/usr/bin/smali",
            System.getProperty("user.home") + "/.local/bin/smali",
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
