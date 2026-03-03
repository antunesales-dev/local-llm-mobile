package com.localllm.chat.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.chat.data.db.entity.MessageEntity
import com.localllm.chat.data.repository.ChatRepository
import com.localllm.chat.llm.ChatMessage
import com.localllm.chat.llm.GenerationParams
import com.localllm.chat.llm.LlmInference
import com.localllm.chat.llm.agent.AgentEvent
import com.localllm.chat.llm.agent.AgentExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val error: String? = null,
    val modelLoaded: Boolean = false,
    val activeToolName: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val llmInference: LlmInference,
    private val agentExecutor: AgentExecutor,
) : ViewModel() {

    val conversationId: Long = savedStateHandle.get<Long>("conversationId") ?: -1L

    val messages: StateFlow<List<MessageEntity>> =
        chatRepository.getMessages(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var generationJob: Job? = null

    init {
        _uiState.value = _uiState.value.copy(modelLoaded = llmInference.getStatus().loaded)
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isGenerating) return

        generationJob = viewModelScope.launch {
            chatRepository.addMessage(conversationId, "user", text)

            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                streamingText = "",
                error = null,
                activeToolName = null,
            )

            val chatMessages = chatRepository.getChatMessages(conversationId)
            val systemPrompt = chatMessages.firstOrNull { it.role == "system" }?.content ?: ""
            val enhancedSystem = agentExecutor.buildSystemPrompt(systemPrompt)

            val messagesWithAgentSystem = buildList {
                add(ChatMessage(role = "system", content = enhancedSystem))
                addAll(chatMessages.filter { it.role != "system" })
            }

            val fullResponse = StringBuilder()

            try {
                agentExecutor.execute(messagesWithAgentSystem, GenerationParams()).collect { event ->
                    when (event) {
                        is AgentEvent.Token -> {
                            fullResponse.append(event.text)
                            _uiState.value = _uiState.value.copy(streamingText = fullResponse.toString())
                        }
                        is AgentEvent.ToolCallStart -> {
                            _uiState.value = _uiState.value.copy(activeToolName = event.toolCall.name)
                        }
                        is AgentEvent.ToolCallResult -> {
                            chatRepository.addMessage(
                                conversationId = conversationId,
                                role = "assistant",
                                content = fullResponse.toString(),
                                toolCallId = event.toolCall.id,
                                toolName = event.toolCall.name,
                                toolArgs = event.toolCall.arguments.toString(),
                                toolResult = event.result.output,
                            )
                            fullResponse.clear()
                            _uiState.value = _uiState.value.copy(
                                streamingText = "",
                                activeToolName = null,
                            )
                        }
                        is AgentEvent.FinalResponse -> {
                            val responseText = event.fullText.trim()
                            if (responseText.isNotBlank()) {
                                chatRepository.addMessage(conversationId, "assistant", responseText)
                            }
                        }
                        is AgentEvent.Error -> {
                            _uiState.value = _uiState.value.copy(error = event.message)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Generation failed")
                val partial = fullResponse.toString().trim()
                if (partial.isNotBlank()) {
                    chatRepository.addMessage(conversationId, "assistant", partial)
                }
            }

            _uiState.value = _uiState.value.copy(
                isGenerating = false,
                streamingText = "",
                activeToolName = null,
            )
        }
    }

    fun stopGeneration() {
        llmInference.cancelGeneration()
        generationJob?.cancel()
        _uiState.value = _uiState.value.copy(isGenerating = false, streamingText = "")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
