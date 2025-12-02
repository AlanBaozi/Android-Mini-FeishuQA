package com.example.feishuqa.app.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.feishuqa.common.utils.JsonUtils
import com.example.feishuqa.data.entity.Conversation
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 历史对话 ViewModel
 * 不使用索引文件，通过遍历目录加载所有对话文件
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _uiState = MutableLiveData<HistoryUiState>(HistoryUiState())
    val uiState: LiveData<HistoryUiState> = _uiState

    init {
        loadConversations()
    }

    /**
     * 从所有对话文件中加载对话列表
     * 遍历目录，读取每个文件，提取信息
     */
    fun loadConversations() {
        viewModelScope.launch {
            val currentState = _uiState.value ?: HistoryUiState()
            _uiState.value = currentState.copy(isLoading = true, error = null)
            
            try {
                // 1. 获取所有对话文件名
                val fileNames = JsonUtils.getAllConversationFiles(context)
                
                // 2. 读取每个文件，提取信息
                val conversations = mutableListOf<Conversation>()
                fileNames.forEach { fileName ->
                    try {
                        val conversation = loadConversationFromFile(fileName)
                        if (conversation != null) {
                            conversations.add(conversation)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // 单个文件读取失败，继续处理其他文件
                    }
                }
                
                _uiState.value = currentState.copy(
                    conversations = conversations,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    /**
     * 从单个对话文件中加载对话信息
     */
    private fun loadConversationFromFile(fileName: String): Conversation? {
        return try {
            // 1. 读取文件内容
            val content = JsonUtils.readJsonFromFiles(context, fileName)
            if (content.isBlank() || content == "[]") {
                return null
            }

            // 2. 解析 JSON
            val json = JSONObject(content)
            
            // 3. 从文件名提取 ID
            val id = JsonUtils.extractIdFromFileName(fileName)
            
            // 4. 提取标题
            val title = json.optString("title", "新对话")
            
            // 5. 提取最后一条消息
            val lastMessage = if (json.has("messages") && !json.isNull("messages")) {
                val messagesArray = json.getJSONArray("messages")
                if (messagesArray.length() > 0) {
                    val lastMsg = messagesArray.getJSONObject(messagesArray.length() - 1)
                    lastMsg.optString("content", null)
                } else {
                    null
                }
            } else {
                null
            }
            
            // 6. 提取时间信息
            val updatedTime = json.optLong("updatedTime", System.currentTimeMillis())
            val createdTime = json.optLong("createdTime", updatedTime)
            
            // 7. 统计消息数量
            val messageCount = if (json.has("messages") && !json.isNull("messages")) {
                json.getJSONArray("messages").length()
            } else {
                0
            }
            
            // 8. 提取其他信息
            val isPinned = json.optBoolean("isPinned", false)
            val userId = if (json.has("userId")) json.getInt("userId") else null
            
            // 9. 创建 Conversation 对象
            Conversation(
                id = id,
                title = title,
                lastMessage = lastMessage,
                lastMessageTime = updatedTime,
                messageCount = messageCount,
                isPinned = isPinned,
                createdAt = createdTime,
                updatedTime = updatedTime,
                userId = userId
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 更新搜索关键词
     */
    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value ?: HistoryUiState()
        _uiState.value = currentState.copy(searchQuery = query)
    }

    /**
     * 选择对话
     */
    fun selectConversation(conversationId: String) {
        val currentState = _uiState.value ?: HistoryUiState()
        _uiState.value = currentState.copy(selectedConversationId = conversationId)
    }
}
