package com.localllm.chat.data.repository

import com.localllm.chat.data.db.ChatDao
import com.localllm.chat.data.db.entity.ConversationEntity
import com.localllm.chat.data.db.entity.MessageEntity
import com.localllm.chat.llm.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
) {

    fun getAllConversations(): Flow<List<ConversationEntity>> =
        chatDao.getAllConversations()

    suspend fun getConversation(id: Long): ConversationEntity? =
        chatDao.getConversation(id)

    suspend fun createConversation(
        title: String,
        modelId: String,
        systemPrompt: String = "",
    ): Long = chatDao.insertConversation(
        ConversationEntity(
            title = title,
            modelId = modelId,
            systemPrompt = systemPrompt,
        ),
    )

    suspend fun updateConversationTitle(id: Long, title: String) {
        val conversation = chatDao.getConversation(id) ?: return
        chatDao.updateConversation(conversation.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(id: Long) =
        chatDao.deleteConversation(id)

    fun getMessages(conversationId: Long): Flow<List<MessageEntity>> =
        chatDao.getMessages(conversationId)

    suspend fun getChatMessages(conversationId: Long): List<ChatMessage> {
        val conversation = chatDao.getConversation(conversationId) ?: return emptyList()
        val messages = chatDao.getMessagesList(conversationId)

        return buildList {
            if (conversation.systemPrompt.isNotBlank()) {
                add(ChatMessage(role = "system", content = conversation.systemPrompt))
            }
            messages.forEach { msg ->
                add(ChatMessage(role = msg.role, content = msg.content))
            }
        }
    }

    suspend fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        toolCallId: String? = null,
        toolName: String? = null,
        toolArgs: String? = null,
        toolResult: String? = null,
    ): Long {
        val id = chatDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                toolCallId = toolCallId,
                toolName = toolName,
                toolArgs = toolArgs,
                toolResult = toolResult,
            ),
        )
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            chatDao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
        }
        return id
    }

    suspend fun updateMessageContent(messageId: Long, content: String) {
        val msg = chatDao.getMessage(messageId) ?: return
        chatDao.updateMessage(msg.copy(content = content))
    }
}
