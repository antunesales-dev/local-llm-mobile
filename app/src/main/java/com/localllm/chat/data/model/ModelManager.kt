package com.localllm.chat.data.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val modelId: String, val progress: Float) : DownloadState
    data class Failed(val modelId: String, val error: String) : DownloadState
}

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val modelsDir = File(context.filesDir, "models")

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    init {
        modelsDir.mkdirs()
    }

    fun getModelPath(model: ModelInfo): String {
        return File(modelsDir, model.fileName).absolutePath
    }

    fun isModelDownloaded(model: ModelInfo): Boolean {
        val file = File(modelsDir, model.fileName)
        return file.exists() && file.length() > 0
    }

    fun getDownloadedModels(): List<ModelInfo> {
        return SupportedModels.models.filter { isModelDownloaded(it) }
    }

    suspend fun downloadModel(model: ModelInfo): Result<String> = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, "${model.fileName}.tmp")

        _downloadState.value = DownloadState.Downloading(model.id, 0f)

        try {
            val request = Request.Builder().url(model.downloadUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val error = "Download failed: HTTP ${response.code}"
                _downloadState.value = DownloadState.Failed(model.id, error)
                return@withContext Result.failure(Exception(error))
            }

            val body = response.body ?: run {
                val error = "Empty response body"
                _downloadState.value = DownloadState.Failed(model.id, error)
                return@withContext Result.failure(Exception(error))
            }

            val contentLength = body.contentLength()
            var bytesWritten = 0L

            tempFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesWritten += read
                        if (contentLength > 0) {
                            _downloadState.value = DownloadState.Downloading(
                                model.id,
                                bytesWritten.toFloat() / contentLength,
                            )
                        }
                    }
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                val error = "Failed to save model file"
                _downloadState.value = DownloadState.Failed(model.id, error)
                return@withContext Result.failure(Exception(error))
            }
            _downloadState.value = DownloadState.Idle
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            tempFile.delete()
            _downloadState.value = DownloadState.Failed(model.id, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun deleteModel(model: ModelInfo) {
        File(modelsDir, model.fileName).delete()
    }
}
