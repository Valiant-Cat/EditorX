package editorx.gui.core

import org.slf4j.LoggerFactory
import java.io.File

/**
 * 应用重启辅助类
 */
object RestartHelper {
    private val logger = LoggerFactory.getLogger(RestartHelper::class.java)

    /**
     * 重启应用
     * 尝试启动新的应用实例，然后退出当前实例
     */
    fun restart() {
        try {
            val command = buildRestartCommand()
            if (command != null) {
                logger.info("正在重启应用，命令: ${command.joinToString(" ")}")
                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(null) // 使用当前工作目录
                processBuilder.start()
                
                // 给新进程一点时间启动
                Thread.sleep(500)
                
                logger.info("新应用实例已启动，退出当前实例")
                System.exit(0)
            } else {
                logger.warn("无法确定重启命令，仅退出应用")
                System.exit(0)
            }
        } catch (e: Exception) {
            logger.error("重启应用失败", e)
            // 即使重启失败，也退出应用
            System.exit(0)
        }
    }

    /**
     * 构建重启命令
     */
    private fun buildRestartCommand(): List<String>? {
        // 方法1: 检查是否从 JAR 文件运行
        val jarPath = getJarPath()
        if (jarPath != null) {
            val javaHome = System.getProperty("java.home")
            val javaExe = File(javaHome, "bin/java").let { 
                if (it.exists()) it.absolutePath
                else File(javaHome, "bin/java.exe").takeIf { it.exists() }?.absolutePath
            } ?: "java"
            
            return listOf(javaExe, "-jar", jarPath)
        }

        // 方法2: 检查是否通过 gradle run 运行
        // 尝试在多个可能的位置查找 gradlew
        val possibleDirs = listOf(
            System.getProperty("user.dir"),
            File(System.getProperty("user.dir")).parent,
            File(System.getProperty("user.dir")).parentFile?.parent
        ).filterNotNull().distinct()
        
        for (dir in possibleDirs) {
            val gradleWrapper = File(dir, "gradlew")
            val gradleWrapperBat = File(dir, "gradlew.bat")
            if (gradleWrapper.exists() && gradleWrapper.canExecute()) {
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val wrapperCmd = if (isWindows) {
                    if (gradleWrapperBat.exists()) gradleWrapperBat.absolutePath else gradleWrapper.absolutePath
                } else {
                    gradleWrapper.absolutePath
                }
                return listOf(wrapperCmd, ":gui:run")
            }
        }

        // 方法3: 尝试使用系统属性中的命令
        val sunJavaCommand = System.getProperty("sun.java.command")
        if (sunJavaCommand != null && sunJavaCommand.contains("editorx")) {
            // 可能是通过 IDE 或其他方式启动的
            // 尝试解析命令
            val parts = sunJavaCommand.split("\\s+".toRegex())
            if (parts.isNotEmpty()) {
                val mainClass = parts.first()
                if (mainClass.contains("editorx")) {
                    val javaHome = System.getProperty("java.home")
                    val javaExe = File(javaHome, "bin/java").let { 
                        if (it.exists()) it.absolutePath
                        else File(javaHome, "bin/java.exe").takeIf { it.exists() }?.absolutePath
                    } ?: "java"
                    
                    val classpath = System.getProperty("java.class.path")
                    return listOf(javaExe, "-cp", classpath, mainClass)
                }
            }
        }

        return null
    }

    /**
     * 获取当前运行的 JAR 文件路径
     */
    private fun getJarPath(): String? {
        // 检查保护域
        val codeSource = RestartHelper::class.java.protectionDomain?.codeSource
        val location = codeSource?.location
        
        if (location != null) {
            try {
                val file = File(location.toURI())
                if (file.exists() && file.name.endsWith(".jar")) {
                    return file.absolutePath
                }
            } catch (e: Exception) {
                logger.debug("无法解析 codeSource location", e)
            }
        }

        // 检查系统属性
        val jarPath = System.getProperty("app.jar.path")
        if (jarPath != null && File(jarPath).exists()) {
            return jarPath
        }

        return null
    }
}

