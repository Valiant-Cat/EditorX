package editorx.core.services

import java.io.File

/**
 * 反编译能力抽象，供 JADX/其他插件实现。
 */
interface DecompilerService {
    fun decompile(input: File, outputDir: File, options: Map<String, Any?> = emptyMap()): DecompileResult

    data class DecompileResult(
        val success: Boolean,
        val message: String? = null,
        val outputDir: File? = null,
    )
}
