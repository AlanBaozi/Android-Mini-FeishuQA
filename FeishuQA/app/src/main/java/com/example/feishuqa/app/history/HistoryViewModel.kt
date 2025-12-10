package com.example.feishuqa.app.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.feishuqa.data.entity.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 历史对话 ViewModel
 * 使用新的索引文件结构，通过 HistoryModel 访问数据
 * 使用 StateFlow 管理 UI 状态（StateFlow 可以同时用于 Compose 和 XML UI）
 */
class HistoryViewModel(private val context: Context) : ViewModel() {

    private val historyModel = HistoryModel(context)

    // UI 状态流
    private val _uiStateFlow = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiStateFlow.asStateFlow()

    // 更新 UI 状态
    private fun updateUiState(newState: HistoryUiState) {
        _uiStateFlow.value = newState
    }

    // 当前登录用户ID（未登录时使用 guest）
    private var currentUserId: String = "guest"

    /**
     * 工厂类，用于创建 HistoryViewModel 实例
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(context) as T
        }
    }

    /**
     * 设置当前用户ID
     */
    fun setUserId(userId: String) {
        currentUserId = userId
        loadConversations()
    }

    /**
     * 从索引文件加载对话列表
     */
    fun loadConversations() {
        viewModelScope.launch {
            val currentState = _uiStateFlow.value
            updateUiState(currentState.copy(isLoading = true, error = null))

            try {
                // 在 IO 线程执行文件操作
                val conversations = withContext(Dispatchers.IO) {
                    historyModel.getAllConversations(currentUserId)
                }
                // 回到主线程更新 UI
                updateUiState(currentState.copy(
                    conversations = conversations,
                    isLoading = false
                ))
            } catch (e: Exception) {
                updateUiState(currentState.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                ))
            }
        }
    }

    /**
     * 更新搜索关键词
     */
    fun updateSearchQuery(query: String) {
        val currentState = _uiStateFlow.value
        updateUiState(currentState.copy(searchQuery = query))
    }

    /**
     * 选择对话
     */
    fun selectConversation(conversationId: String) {
        val currentState = _uiStateFlow.value
        updateUiState(currentState.copy(selectedConversationId = conversationId))
    }

    /**
     * 删除对话
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                // 在 IO 线程执行文件操作
                withContext(Dispatchers.IO) {
                    historyModel.deleteConversation(conversationId)
                }
                // 重新加载列表
                loadConversations()
            } catch (e: Exception) {
                val currentState = _uiStateFlow.value
                updateUiState(currentState.copy(error = e.message ?: "删除失败"))
            }
        }
    }

    /**
     * 重命名对话
     */
    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                // 在 IO 线程执行文件操作
                withContext(Dispatchers.IO) {
                    historyModel.updateConversationTitle(conversationId, newTitle)
                }
                // 重新加载列表
                loadConversations()
            } catch (e: Exception) {
                val currentState = _uiStateFlow.value
                updateUiState(currentState.copy(error = e.message ?: "重命名失败"))
            }
        }
    }

    /**
     * 切换置顶状态
     */
    fun togglePinConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                // 获取当前状态（在主线程读取状态）
                val currentState = _uiStateFlow.value
                val conversation = currentState.conversations.find { it.id == conversationId }
                val newPinnedState = !(conversation?.isPinned ?: false)

                // 在 IO 线程执行文件操作
                withContext(Dispatchers.IO) {
                    historyModel.togglePinConversation(conversationId, newPinnedState)
                }
                // 重新加载列表
                loadConversations()
            } catch (e: Exception) {
                val currentState = _uiStateFlow.value
                updateUiState(currentState.copy(error = e.message ?: "置顶操作失败"))
            }
        }
    }

    /**
     * 创建新对话
     * 注意：这是一个 suspend 函数，必须在协程中调用
     */
    suspend fun createNewConversation(initialTitle: String = "新对话"): String? {
        return try {
            // 在 IO 线程执行文件操作
            val newIndex = withContext(Dispatchers.IO) {
                historyModel.createConversation(currentUserId, initialTitle)
            }
            // 重新加载列表
            loadConversations()
            newIndex.conversationId
        } catch (e: Exception) {
            val currentState = _uiStateFlow.value
            updateUiState(currentState.copy(error = e.message ?: "创建对话失败"))
            null
        }
    }

    /**
     * 清除错误状态
     */
    fun clearError() {
        val currentState = _uiStateFlow.value
        updateUiState(currentState.copy(error = null))
    }

    /**
     * 删除指定用户的所有对话（用于清理未登录用户的临时数据）
     */
    fun deleteConversationsByUserId(userId: String) {
        viewModelScope.launch {
            try {
                // 在 IO 线程执行文件操作
                withContext(Dispatchers.IO) {
                    historyModel.deleteConversationsByUserId(userId)
                }
                // 如果删除的是当前用户的对话，重新加载列表
                if (userId == currentUserId) {
                    loadConversations()
                }
            } catch (e: Exception) {
                val currentState = _uiStateFlow.value
                updateUiState(currentState.copy(error = e.message ?: "删除失败"))
            }
        }
    }
}
