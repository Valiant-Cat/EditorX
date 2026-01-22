package editorx.core

import editorx.core.external.ApkTool
import editorx.core.util.AppPaths

/**
 * 简单的验证脚本，用于确认 apktool 检测逻辑
 */
fun main() {
    println("=== 验证 AppPaths.appHome() ===")
    val appHome = AppPaths.appHome()
    println("appHome: ${appHome.toFile().absolutePath}")
    println("appHome 存在: ${appHome.toFile().exists()}")
    
    val toolsDir = AppPaths.toolsDir()
    println("tools 目录: ${toolsDir.toFile().absolutePath}")
    println("tools 目录存在: ${toolsDir.toFile().exists()}")
    
    val apktoolJar = toolsDir.resolve("apktool.jar").toFile()
    println("apktool.jar 路径: ${apktoolJar.absolutePath}")
    println("apktool.jar 存在: ${apktoolJar.exists()}")
    println("apktool.jar 是文件: ${apktoolJar.isFile}")
    
    val userDir = java.io.File(System.getProperty("user.dir", "."))
    println("\n项目根目录 (user.dir): ${userDir.absolutePath}")
    val expectedApktoolJar = java.io.File(userDir, "tools/apktool.jar")
    println("期望的 apktool.jar: ${expectedApktoolJar.absolutePath}")
    println("期望的 apktool.jar 存在: ${expectedApktoolJar.exists()}")
    
    println("\n=== 验证 ApkTool.isAvailable() ===")
    val isAvailable = ApkTool.isAvailable()
    println("ApkTool.isAvailable(): $isAvailable")
    
    if (isAvailable) {
        println("\n✅ 验证成功！apktool 已找到")
    } else {
        println("\n❌ 验证失败！apktool 未找到")
        System.exit(1)
    }
}
