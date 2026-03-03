package com.localllm.chat.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.chat.data.model.DownloadState
import com.localllm.chat.data.model.ModelInfo
import com.localllm.chat.data.model.ModelManager
import com.localllm.chat.data.model.SupportedModels
import com.localllm.chat.llm.LlmInference
import com.localllm.chat.llm.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelUiState(
    val models: List<ModelInfo> = SupportedModels.models,
    val downloadedModelIds: Set<String> = emptySet(),
    val loadedModelId: String? = null,
    val modelStatus: ModelStatus = ModelStatus(loaded = false),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val llmInference: LlmInference,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState

    val downloadState: StateFlow<DownloadState> =
        modelManager.downloadState
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadState.Idle)

    init {
        refreshState()
    }

    fun refreshState() {
        val downloaded = modelManager.getDownloadedModels().map { it.id }.toSet()
        val status = llmInference.getStatus()
        _uiState.value = _uiState.value.copy(
            downloadedModelIds = downloaded,
            modelStatus = status,
        )
    }

    fun downloadModel(model: ModelInfo) {
        viewModelScope.launch {
            val result = modelManager.downloadModel(model)
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
            refreshState()
        }
    }

    fun loadModel(model: ModelInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val path = modelManager.getModelPath(model)
            val success = llmInference.loadModel(path)

            if (success) {
                _uiState.value = _uiState.value.copy(
                    loadedModelId = model.id,
                    isLoading = false,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load model. Device may not have enough memory.",
                )
            }
            refreshState()
        }
    }

    fun unloadModel() {
        llmInference.unloadModel()
        _uiState.value = _uiState.value.copy(loadedModelId = null)
        refreshState()
    }

    fun deleteModel(model: ModelInfo) {
        if (_uiState.value.loadedModelId == model.id) {
            unloadModel()
        }
        modelManager.deleteModel(model)
        refreshState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
