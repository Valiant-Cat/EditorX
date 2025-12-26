package editorx.core.util

import java.util.*

object SystemUtils {

    fun isMacOS(): Boolean = getOsName().lowercase(Locale.ROOT).contains("mac")

    fun getOsName(): String = System.getProperty("os.name", "")
}