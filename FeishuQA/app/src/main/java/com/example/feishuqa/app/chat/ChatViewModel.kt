package com.example.feishuqa.app.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feishuqa.data.entity.Message
import com.example.feishuqa.data.entity.MessageStatus
import com.example.feishuqa.data.entity.MessageType
import com.example.feishuqa.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoadingMore: Boolean = false,
    val isTyping: Boolean = false, // AI 是否正在输入
    val error: String? = null
) {
    companion object {
        val Empty = ChatUiState()
    }
}

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()
    
    // 当前会话 ID
    private val conversationId = "1" 

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
    }

    fun loadMessages() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingMore = true, error = null) }
                
                val history = repository.getMessages(conversationId, page = 1)
                _uiState.update { it.copy(
                    messages = history,
                    isLoadingMore = false,
                    error = null
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoadingMore = false,
                    error = "加载消息失败: ${e.message}"
                ) }
            }
        }
    }

    // 发送文本消息
    fun sendTextMessage(content: String) {
        if (content.isBlank()) return

        val userMsg = Message(
            conversationId = conversationId,
            senderId = "user",
            type = MessageType.TEXT,
            content = content,
            status = MessageStatus.SENDING
        )

        // 1. 立即更新 UI 显示用户消息
        updateMessageList(userMsg)

        viewModelScope.launch {
            try {
                // 2. 发送给服务端（模拟）
                val success = repository.sendMessage(userMsg)
                if (success) {
                    updateMessageStatus(userMsg.id, MessageStatus.SENT)
                    // 3. 触发 AI 回复
                    triggerAiResponse(content)
                } else {
                    updateMessageStatus(userMsg.id, MessageStatus.FAILED)
                }
            } catch (e: Exception) {
                updateMessageStatus(userMsg.id, MessageStatus.FAILED)
            }
        }
    }

    // 触发 AI 回复 (打字机效果)
    private fun triggerAiResponse(userQuery: String) {
        val aiMsgId = UUID.randomUUID().toString()
        val initialAiMsg = Message(
            id = aiMsgId,
            conversationId = conversationId,
            senderId = "ai",
            type = MessageType.TEXT,
            content = "正在思考中...", // 初始占位符
            status = MessageStatus.TYPING
        )

        updateMessageList(initialAiMsg)
        _uiState.update { it.copy(isTyping = true) }

        viewModelScope.launch {
            try {
                repository.streamAiResponse(userQuery, conversationId).collect { partialContent ->
                    // 更新消息内容
                    _uiState.update { state ->
                        val updatedList = state.messages.map { msg ->
                            if (msg.id == aiMsgId) {
                                msg.copy(content = partialContent, status = MessageStatus.TYPING)
                            } else {
                                msg
                            }
                        }
                        state.copy(messages = updatedList, error = null)
                    }
                }
                // 结束输入状态
                _uiState.update { state ->
                    val updatedList = state.messages.map { msg ->
                        if (msg.id == aiMsgId) msg.copy(status = MessageStatus.SENT) else msg
                    }
                    state.copy(messages = updatedList, isTyping = false)
                }
            } catch (e: Exception) {
                // AI回复失败处理
                _uiState.update { state ->
                    val updatedList = state.messages.map { msg ->
                        if (msg.id == aiMsgId) msg.copy(status = MessageStatus.FAILED) else msg
                    }
                    state.copy(messages = updatedList, isTyping = false, error = "AI回复失败: ${e.message}")
                }
            }
        }
    }

    private fun updateMessageList(newMessage: Message) {
        _uiState.update { state ->
            // 注意：我们使用 reverseLayout，所以新消息加到列表头部 (索引0)
            state.copy(messages = listOf(newMessage) + state.messages)
        }
    }

    private fun updateMessageStatus(id: String, status: MessageStatus) {
        _uiState.update { state ->
            val updatedList = state.messages.map {
                if (it.id == id) it.copy(status = status) else it
            }
            state.copy(messages = updatedList)
        }
    }
}