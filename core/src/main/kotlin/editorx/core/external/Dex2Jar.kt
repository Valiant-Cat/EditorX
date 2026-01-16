package editorx.core.external

import com.googlecode.d2j.dex.Dex2jar
import java.io.File

/**
 * 对 dex2jar 的封装：负责将 DEX 转换为 classes.jar。
 */
object Dex2Jar {
    enum class Status { SUCCESS, FAILED }

    data class RunResult(val status: Status, val output: String)

    fun convert(dexFile: File, outputJar: File): RunResult {
        return try {
            Dex2jar.from(dexFile).to(outputJar.toPath())
            RunResult(Status.SUCCESS, "ok")
        } catch (e: Throwable) {
            RunResult(Status.FAILED, e.message ?: "dex2jar failed")
        }
    }
}
