package editorx.gui.ai

import editorx.core.util.FileStore
import java.io.File

class AiConfigStore(appDir: File) {
    private val configFile = File(appDir, "ai.properties")

    fun load(): AiSettings {
        val store = store()
        val modelIds = store.get("ai.models", "")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val models = modelIds.mapNotNull { id -> loadModel(store, id) }
        val active = store.get("ai.active", null)?.takeIf { it.isNotBlank() } ?: models.firstOrNull()?.id
        return AiSettings(activeModelId = active, models = models)
    }

    fun ensureTemplate(): File {
        if (configFile.exists()) return configFile
        val store = store()
        store.put("ai.active", "model-1")
        store.put("ai.models", "model-1,model-2")

        seedModel(
            store = store,
            id = "model-1",
            name = "主模型",
            baseUrl = "https://api.openai.com/v1"
        )
        seedModel(
            store = store,
            id = "model-2",
            name = "备用模型",
            baseUrl = "https://api.openai.com/v1"
        )
        store.sync()
        return configFile
    }

    fun saveActive(modelId: String) {
        val store = store()
        store.put("ai.active", modelId)
        store.sync()
    }

    fun getConfigFile(): File = configFile

    private fun seedModel(store: FileStore, id: String, name: String, baseUrl: String) {
        val prefix = "ai.model.$id."
        store.put(prefix + "name", name)
        store.put(prefix + "provider", AiProviderType.OPENAI_RESPONSES.id)
        store.put(prefix + "baseUrl", baseUrl)
        store.put(prefix + "apiKey", "")
        store.put(prefix + "model", "")
        store.put(prefix + "stream", "true")
        store.put(prefix + "temperature", "0.7")
    }

    private fun loadModel(store: FileStore, id: String): AiModelConfig? {
        val prefix = "ai.model.$id."
        val name = store.get(prefix + "name", id) ?: id
        val providerId = store.get(prefix + "provider", AiProviderType.OPENAI_RESPONSES.id) ?: AiProviderType.OPENAI_RESPONSES.id
        val provider = AiProviderType.values().firstOrNull { it.id == providerId } ?: AiProviderType.OPENAI_RESPONSES
        val model = store.get(prefix + "model", "") ?: ""
        val baseUrl = store.get(prefix + "baseUrl", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        val apiKey = store.get(prefix + "apiKey", "") ?: ""
        val temperature = store.get(prefix + "temperature", null)?.toDoubleOrNull()
        val stream = store.get(prefix + "stream", "true")?.toBoolean() ?: true

        return AiModelConfig(
            id = id,
            name = name,
            provider = provider,
            model = model,
            baseUrl = baseUrl,
            apiKey = apiKey,
            temperature = temperature,
            stream = stream
        )
    }

    private fun store(): FileStore = FileStore(configFile)
}
