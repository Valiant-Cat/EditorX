package editorx.syntax

import java.io.File

/**
 * 文档选择器
 * 用于匹配文件和语言
 */
@FunctionalInterface
interface DocumentSelector {
    fun matches(file: File): Boolean

    companion object {
        /**
         * 根据文件扩展名创建选择器
         */
        fun forExtensions(vararg extensions: String): DocumentSelector {
            val extSet = extensions.map { ext ->
                if (ext.startsWith(".")) ext else ".$ext"
            }.toSet()

            return object : DocumentSelector {
                override fun matches(file: File): Boolean {
                    val name = file.name
                    return extSet.any { ext -> name.endsWith(ext) }
                }
            }
        }
    }
}
