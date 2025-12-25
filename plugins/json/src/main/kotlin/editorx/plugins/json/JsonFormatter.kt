package editorx.plugins.json

import editorx.core.filetype.Formatter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/**
 * JSON 格式化器
 */
object JsonFormatter : Formatter {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setLenient()
        .create()

    override fun format(content: String): String {
        if (content.isBlank()) return content

        return try {
            // 解析 JSON
            val jsonElement = JsonParser.parseString(content)
            // 格式化输出
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            throw Exception("JSON 格式化失败: ${e.message}", e)
        }
    }
}

