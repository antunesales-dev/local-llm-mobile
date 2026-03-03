package com.localllm.chat.data.model

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val quantization: String,
    val downloadUrl: String,
    val fileName: String,
)

object SupportedModels {

    val models = listOf(
        ModelInfo(
            id = "qwen3.5-9b-q4_k_m",
            name = "Qwen 3.5 9B (Q4_K_M)",
            description = "Qwen 3.5 9B, 4-bit quantized. Best balance of quality and speed for flagship devices (12GB+ RAM).",
            sizeBytes = 5_680_000_000L,
            quantization = "Q4_K_M",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-9B-GGUF/resolve/main/Qwen3.5-9B-Q4_K_M.gguf",
            fileName = "Qwen3.5-9B-Q4_K_M.gguf",
        ),
        ModelInfo(
            id = "qwen3.5-9b-q2_k_xl",
            name = "Qwen 3.5 9B (Q2_K_XL)",
            description = "Qwen 3.5 9B, 2-bit dynamic quantized. Fits on 8GB+ devices with acceptable quality.",
            sizeBytes = 3_970_000_000L,
            quantization = "Q2_K_XL",
            downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-9B-GGUF/resolve/main/Qwen3.5-9B-UD-Q2_K_XL.gguf",
            fileName = "Qwen3.5-9B-UD-Q2_K_XL.gguf",
        ),
    )

    fun findById(id: String): ModelInfo? = models.find { it.id == id }
}
