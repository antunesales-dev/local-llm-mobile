package com.localllm.chat.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ChatMessage(
    val role: String,
    val content: String,
)

data class ModelStatus(
    val loaded: Boolean,
    val description: String = "",
    val parametersM: Int = 0,
)

data class GenerationParams(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 2048,
)

@Singleton
class LlmInference @Inject constructor(
    private val bridge: LlamaBridge,
) {

    suspend fun loadModel(
        path: String,
        contextSize: Int = 4096,
        gpuLayers: Int = 0,
    ): Boolean = withContext(Dispatchers.IO) {
        bridge.loadModel(path, contextSize, gpuLayers)
    }

    fun unloadModel() {
        bridge.unloadModel()
    }

    fun getStatus(): ModelStatus {
        if (!bridge.isModelLoaded()) {
            return ModelStatus(loaded = false)
        }
        val infoJson = bridge.getModelInfo()
        val json = JSONObject(infoJson)
        return ModelStatus(
            loaded = true,
            description = json.optString("description", ""),
            parametersM = json.optInt("parameters_m", 0),
        )
    }

    fun updateParams(params: GenerationParams) {
        bridge.updateSamplerParams(
            params.temperature,
            params.topP,
            params.topK,
            params.repeatPenalty,
        )
    }

    fun generateResponse(
        messages: List<ChatMessage>,
        params: GenerationParams = GenerationParams(),
    ): Flow<String> = callbackFlow {
        withContext(Dispatchers.IO) {
            updateParams(params)

            val roles = messages.map { it.role }.toTypedArray()
            val contents = messages.map { it.content }.toTypedArray()
            val prompt = bridge.applyChatTemplate(roles, contents)

            bridge.generate(prompt, params.maxTokens) { token ->
                trySend(token)
            }
        }
        close()
        awaitClose { bridge.cancelGeneration() }
    }

    fun cancelGeneration() {
        bridge.cancelGeneration()
    }
}
