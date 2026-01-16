package editorx.gui.ai

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class OpenAiResponsesClient {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun send(
        modelConfig: AiModelConfig,
        messages: List<AiMessage>,
        onDelta: ((String) -> Unit)?,
    ): AiChatResult {
        val requestBody = buildRequestBody(modelConfig, messages)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(resolveResponsesUrl(modelConfig.baseUrl)))
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", "Bearer ${modelConfig.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        return if (modelConfig.stream && onDelta != null) {
            sendStreaming(request, onDelta)
        } else {
            sendOnce(request)
        }
    }

    private fun sendOnce(request: HttpRequest): AiChatResult {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() / 100 != 2) {
            val message = extractErrorMessage(response.body())
            return AiChatResult.Error(message ?: "请求失败，HTTP ${response.statusCode()}")
        }
        val text = extractOutputText(response.body())
        if (text.isBlank()) {
            val message = extractErrorMessage(response.body())
            return AiChatResult.Error(message ?: "未返回有效内容")
        }
        return AiChatResult.Success(text)
    }

    private fun sendStreaming(request: HttpRequest, onDelta: (String) -> Unit): AiChatResult {
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() / 100 != 2) {
            val body = response.body().use { it.readBytes().toString(StandardCharsets.UTF_8) }
            val message = extractErrorMessage(body)
            return AiChatResult.Error(message ?: "请求失败，HTTP ${response.statusCode()}")
        }

        val sb = StringBuilder()
        response.body().use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break
                    val element = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: continue
                    val obj = element as? JsonObject ?: continue
                    val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()

                    if (type.contains("output_text.delta")) {
                        val delta = obj["delta"]?.jsonPrimitive?.contentOrNull
                            ?: obj["text"]?.jsonPrimitive?.contentOrNull
                        if (!delta.isNullOrBlank()) {
                            sb.append(delta)
                            onDelta(delta)
                        }
                    } else if (type == "response.completed") {
                        break
                    } else if (type == "error") {
                        val message = obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                        return AiChatResult.Error(message ?: "请求失败")
                    }
                }
            }
        }

        val result = sb.toString()
        return if (result.isBlank()) AiChatResult.Error("未返回有效内容") else AiChatResult.Success(result)
    }

    private fun buildRequestBody(modelConfig: AiModelConfig, messages: List<AiMessage>): String {
        val inputItems = buildJsonArray {
            messages.forEach { msg ->
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive(msg.role.wireValue))
                        put("content", JsonPrimitive(msg.content))
                    }
                )
            }
        }

        val payload = buildJsonObject {
            put("model", JsonPrimitive(modelConfig.model))
            put("input", inputItems)
            put("stream", JsonPrimitive(modelConfig.stream))
            modelConfig.temperature?.let { put("temperature", JsonPrimitive(it)) }
        }

        return json.encodeToString(JsonElement.serializer(), payload)
    }

    private fun resolveResponsesUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/v1")) {
            "$trimmed/responses"
        } else {
            "$trimmed/v1/responses"
        }
    }

    private fun extractOutputText(body: String): String {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return ""
        val obj = element.jsonObject
        val output = obj["output"] as? JsonArray ?: return ""
        val sb = StringBuilder()
        output.forEach { item ->
            val itemObj = item.jsonObject
            if (itemObj["type"]?.jsonPrimitive?.contentOrNull != "message") return@forEach
            val content = itemObj["content"]?.jsonArray ?: return@forEach
            content.forEach { part ->
                val partObj = part.jsonObject
                if (partObj["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                    val text = partObj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    sb.append(text)
                }
            }
        }
        return sb.toString()
    }

    private fun extractErrorMessage(body: String): String? {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        val error = element.jsonObject["error"] as? JsonObject ?: return null
        return error["message"]?.jsonPrimitive?.contentOrNull
    }
}

sealed class AiChatResult {
    data class Success(val text: String) : AiChatResult()
    data class Error(val message: String) : AiChatResult()
}
