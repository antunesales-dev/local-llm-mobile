package com.localllm.chat.llm

import javax.inject.Inject
import javax.inject.Singleton

fun interface TokenCallback {
    fun onToken(token: String)
}

@Singleton
class LlamaBridge @Inject constructor() {

    init {
        System.loadLibrary("localllm")
    }

    external fun loadModel(modelPath: String, nCtx: Int, nGpuLayers: Int): Boolean
    external fun unloadModel()
    external fun isModelLoaded(): Boolean
    external fun updateSamplerParams(temperature: Float, topP: Float, topK: Int, repeatPenalty: Float)
    external fun generate(prompt: String, maxTokens: Int, callback: TokenCallback?): String
    external fun cancelGeneration()
    external fun getModelInfo(): String
    external fun applyChatTemplate(roles: Array<String>, contents: Array<String>): String
}
