package editor.util

import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.util.jar.JarFile

object ClassScanner {
    fun findSubclasses(packageName: String, superClass: Class<*>): Set<Class<*>> {
        val result: MutableSet<Class<*>> = HashSet()
        val packagePath = packageName.replace('.', '/')
        
        println("ClassScanner: 开始扫描包 $packageName (路径: $packagePath)")

        try {
            val classLoader = Thread.currentThread().contextClassLoader
            val resources = classLoader.getResources(packagePath)
            
            var resourceCount = 0
            while (resources.hasMoreElements()) {
                val resource = resources.nextElement()
                resourceCount++
                println("ClassScanner: 找到资源 $resourceCount: $resource (协议: ${resource.protocol})")
                
                if (resource.protocol == "file") {
                    // 扫描文件系统
                    val dir = File(resource.file)
                    println("ClassScanner: 扫描文件系统目录: ${dir.absolutePath}")
                    scanDirectory(dir, packageName, superClass, result)
                } else if (resource.protocol == "jar") {
                    // 扫描 JAR 文件
                    val jar = (resource.openConnection() as JarURLConnection).jarFile
                    println("ClassScanner: 扫描JAR文件: ${jar.name}")
                    scanJar(jar, packagePath, superClass, result)
                }
            }
            
            if (resourceCount == 0) {
                println("ClassScanner: 未找到包 $packageName 的资源")
            }
        } catch (e: IOException) {
            println("ClassScanner: 扫描包 $packageName 时出错: ${e.message}")
            e.printStackTrace()
        }

        println("ClassScanner: 包 $packageName 扫描完成，找到 ${result.size} 个插件类")
        return result
    }

    private fun scanDirectory(dir: File, packageName: String, superClass: Class<*>, result: MutableSet<Class<*>>) {
        if (!dir.exists()) {
            println("ClassScanner: 目录不存在: ${dir.absolutePath}")
            return
        }

        val files = dir.listFiles() ?: return
        println("ClassScanner: 扫描目录 ${dir.absolutePath}，包含 ${files.size} 个文件/目录")

        for (file in files) {
            if (file.isDirectory) {
                println("ClassScanner: 进入子目录: ${file.name}")
                scanDirectory(file, packageName + "." + file.name, superClass, result)
            } else if (file.name.endsWith(".class")) {
                val className = packageName + "." + file.name.substring(0, file.name.length - 6)
                println("ClassScanner: 检查类文件: $className")
                checkClass(className, superClass, result)
            }
        }
    }

    private fun scanJar(jar: JarFile, packagePath: String, superClass: Class<*>, result: MutableSet<Class<*>>) {
        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name
            if (name.startsWith(packagePath) && name.endsWith(".class")) {
                val className = name.replace('/', '.').substring(0, name.length - 6)
                checkClass(className, superClass, result)
            }
        }
    }

    private fun checkClass(className: String, superClass: Class<*>, result: MutableSet<Class<*>>) {
        try {
            val clazz = Class.forName(className)
            val isAssignable = superClass.isAssignableFrom(clazz)
            val isNotSuperClass = clazz != superClass
            
            println("ClassScanner: 检查类 $className - 可分配: $isAssignable, 不是超类: $isNotSuperClass")
            
            if (isAssignable && isNotSuperClass) {
                println("ClassScanner: 找到插件类: $className")
                result.add(clazz)
            }
        } catch (e: ClassNotFoundException) {
            println("ClassScanner: 无法加载类 $className: ${e.message}")
        } catch (e: NoClassDefFoundError) {
            println("ClassScanner: 类定义未找到 $className: ${e.message}")
        } catch (e: UnsatisfiedLinkError) {
            println("ClassScanner: 链接错误 $className: ${e.message}")
        }
    }
}
