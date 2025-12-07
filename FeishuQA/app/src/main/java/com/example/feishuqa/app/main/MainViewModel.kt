package com.example.feishuqa.app.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feishuqa.common.utils.SessionManager
import com.example.feishuqa.data.entity.AIModel
import com.example.feishuqa.data.entity.AIModels
import com.example.feishuqa.data.entity.Conversation
import com.example.feishuqa.data.repository.MainRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面ViewModel
 * ViewModel层：处理业务逻辑，管理UI状态
 */
class MainViewModel(private val context: Context) : ViewModel() {

    private val repository = MainRepository(context)

    // UI状态
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // 导航事件
    private val _navigateToConversation = MutableStateFlow<String?>(null)
    val navigateToConversation: StateFlow<String?> = _navigateToConversation.asStateFlow()

    // 需要登录提示事件
    private val _requireLogin = MutableStateFlow(false)
    val requireLogin: StateFlow<Boolean> = _requireLogin.asStateFlow()

    init {
        refreshLoginState()
        loadConversations()
    }

    /**
     * 刷新登录状态
     */
    fun refreshLoginState() {
        val isLoggedIn = SessionManager.isLoggedIn(context)
        val userName = SessionManager.getUserName(context)
        val userId = SessionManager.getUserId(context)
        
        _uiState.value = _uiState.value.copy(
            isLoggedIn = isLoggedIn,
            userName = userName,
            userId = userId
        )
        
        // 如果已登录，设置用户ID给repository
        if (isLoggedIn && userId != null) {
            repository.setUserId(userId)
        }
    }

    /**
     * 退出登录
     */
    fun logout() {
        SessionManager.clearSession(context)
        _uiState.value = _uiState.value.copy(
            isLoggedIn = false,
            userName = null,
            userId = null
        )
    }

    /**
     * 检查是否需要登录（用于创建新对话等操作）
     * @return true表示已登录，false表示未登录
     */
    fun checkLoginRequired(): Boolean {
        if (!SessionManager.isLoggedIn(context)) {
            _requireLogin.value = true
            return false
        }
        return true
    }

    /**
     * 清除登录提示事件
     */
    fun clearRequireLogin() {
        _requireLogin.value = false
    }

    /**
     * 设置当前用户ID（应该在登录后调用）
     */
    fun setUserId(userId: String) {
        repository.setUserId(userId)
        loadConversations() // 重新加载对话列表
    }

    /**
     * 加载历史对话列表
     */
    fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val conversations = repository.getConversations()
                _uiState.value = _uiState.value.copy(
                    conversations = conversations,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * 切换联网搜索状态
     */
    fun toggleWebSearch() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(isWebSearchEnabled = !currentState.isWebSearchEnabled)
    }

    /**
     * 切换输入模式
     */
    fun toggleInputMode() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(isTextInputMode = !currentState.isTextInputMode)
    }

    /**
     * 选择AI模型
     */
    fun selectModel(model: AIModel) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }

    /**
     * 发送消息
     */
    fun sendMessage(text: String) {
        viewModelScope.launch {
            // TODO: 实际发送消息到API
            // 这里可以调用Repository发送消息
        }
    }

    /**
     * 创建新对话
     */
    fun createNewConversation(title: String) {
        // 检查是否已登录
        if (!checkLoginRequired()) {
            return
        }
        
        viewModelScope.launch {
            val result = repository.createConversation(title)
            result.onSuccess { conversationId ->
                _navigateToConversation.value = conversationId
                loadConversations() // 刷新列表
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = it.message)
            }
        }
    }

    /**
     * 选择对话
     */
    fun selectConversation(conversationId: String) {
        _uiState.value = _uiState.value.copy(selectedConversationId = conversationId)
        _navigateToConversation.value = conversationId
    }

    /**
     * 清除导航事件
     */
    fun clearNavigation() {
        _navigateToConversation.value = null
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 获取可用模型列表
     */
    fun getAvailableModels(): List<AIModel> {
        return repository.getAvailableModels()
    }

    /**
     * 更新搜索关键词
     */
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /**
     * 删除对话
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversationId)
                loadConversations() // 重新加载列表
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /**
     * 重命名对话
     */
    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                repository.renameConversation(conversationId, newTitle)
                loadConversations() // 重新加载列表
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /**
     * 切换置顶状态
     */
    fun togglePinConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val conversation = currentState.conversations.find { it.id == conversationId }
                val newPinnedState = !(conversation?.isPinned ?: false)
                repository.togglePinConversation(conversationId, newPinnedState)
                loadConversations() // 重新加载列表
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}

/**
 * 主界面UI状态
 */
data class MainUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedModel: AIModel = AIModels.defaultModel,
    val isWebSearchEnabled: Boolean = false,
    val isTextInputMode: Boolean = true,
    val selectedConversationId: String? = null,
    val searchQuery: String = "", // 搜索关键词
    val isLoggedIn: Boolean = false, // 是否已登录
    val userName: String? = null, // 当前登录用户名
    val userId: String? = null // 当前登录用户ID
) {
    /**
     * 获取过滤后的对话列表（根据搜索关键词和置顶状态排序）
     */
    fun getFilteredConversations(): List<Conversation> {
        val filtered = if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.lastMessage?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        
        // 置顶的排在前面，然后按时间倒序
        return filtered.sortedWith(
            compareByDescending<Conversation> { it.isPinned }
                .thenByDescending { it.lastMessageTime }
        )
    }
}

