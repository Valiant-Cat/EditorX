package editorx.core

import editorx.core.external.ApkTool
import editorx.core.util.AppPaths
import org.junit.jupiter.api.Test
import java.io.File

class AppPathsTest {
    @Test
    fun testAppHomeInDevMode() {
        val appHome = AppPaths.appHome()
        println("appHome: ${appHome.toFile().absolutePath}")
        println("appHome 存在: ${appHome.toFile().exists()}")
        
        val toolsDir = AppPaths.toolsDir()
        println("tools 目录: ${toolsDir.toFile().absolutePath}")
        println("tools 目录存在: ${toolsDir.toFile().exists()}")
        
        val apktoolJar = toolsDir.resolve("apktool.jar").toFile()
        println("apktool.jar 路径: ${apktoolJar.absolutePath}")
        println("apktool.jar 存在: ${apktoolJar.exists()}")
        
        // 验证在开发模式下，appHome 应该是项目根目录
        val userDir = File(System.getProperty("user.dir", "."))
        val expectedToolsDir = File(userDir, "tools/apktool.jar")
        
        println("\n项目根目录: ${userDir.absolutePath}")
        println("期望的 apktool.jar: ${expectedToolsDir.absolutePath}")
        println("期望的 apktool.jar 存在: ${expectedToolsDir.exists()}")
        
        // 如果 apktool.jar 在项目根目录存在，appHome 应该能找到它
        if (expectedToolsDir.exists()) {
            assert(apktoolJar.exists()) {
                "apktool.jar 应该能在 appHome/tools/ 下找到，但实际路径: ${apktoolJar.absolutePath}"
            }
        }
    }
    
    @Test
    fun testApkToolAvailable() {
        val isAvailable = ApkTool.isAvailable()
        println("ApkTool.isAvailable(): $isAvailable")
        
        // 如果 tools/apktool.jar 存在，应该能找到
        val userDir = File(System.getProperty("user.dir", "."))
        val apktoolJar = File(userDir, "tools/apktool.jar")
        if (apktoolJar.exists()) {
            assert(isAvailable) {
                "tools/apktool.jar 存在，但 ApkTool.isAvailable() 返回 false"
            }
        }
    }
}
