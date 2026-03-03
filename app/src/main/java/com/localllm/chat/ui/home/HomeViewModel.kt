package com.localllm.chat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.chat.data.db.entity.ConversationEntity
import com.localllm.chat.data.repository.ChatRepository
import com.localllm.chat.llm.LlmInference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val llmInference: LlmInference,
) : ViewModel() {

    val conversations: StateFlow<List<ConversationEntity>> =
        chatRepository.getAllConversations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded

    init {
        _modelLoaded.value = llmInference.getStatus().loaded
    }

    fun createConversation(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = chatRepository.createConversation(
                title = "New Chat",
                modelId = "",
                systemPrompt = "You are a helpful assistant running locally on an Android device. Be concise and helpful.",
            )
            onCreated(id)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
        }
    }

    fun refreshModelState() {
        _modelLoaded.value = llmInference.getStatus().loaded
    }
}
