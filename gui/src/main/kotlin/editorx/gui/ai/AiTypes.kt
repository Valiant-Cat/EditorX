package editorx.gui.ai

enum class AiRole(val wireValue: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant")
}

data class AiMessage(
    val role: AiRole,
    val content: String
)

enum class AiProviderType(val id: String) {
    OPENAI_RESPONSES("openai_responses")
}

data class AiModelConfig(
    val id: String,
    val name: String,
    val provider: AiProviderType,
    val model: String,
    val baseUrl: String,
    val apiKey: String,
    val temperature: Double?,
    val stream: Boolean
)

data class AiSettings(
    val activeModelId: String?,
    val models: List<AiModelConfig>
)
