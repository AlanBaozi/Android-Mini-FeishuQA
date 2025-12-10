package com.example.feishuqa.app.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feishuqa.common.utils.AiHelper
import com.example.feishuqa.common.utils.SessionManager
import com.example.feishuqa.data.entity.AIModel
import com.example.feishuqa.data.entity.AIModels
import com.example.feishuqa.data.entity.Conversation
import com.example.feishuqa.data.repository.ChatRepositoryExample
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
        // refreshLoginState 内部已经调用了 loadConversations()，这里不需要重复调用
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
        
        // 根据登录状态设置用户ID给repository
        if (isLoggedIn && userId != null) {
            repository.setUserId(userId)
        } else {
            // 未登录时使用 guest 用户ID
            repository.setUserId(MainRepository.GUEST_USER_ID)
        }
        
        // 刷新对话列表
        loadConversations()
    }

    /**
     * 退出登录
     */
    fun logout() {
        // 退出登录前，删除所有 guest 用户的临时对话
        viewModelScope.launch {
            repository.deleteConversationsByUserId(MainRepository.GUEST_USER_ID)
        }
        
        SessionManager.clearSession(context)
        _uiState.value = _uiState.value.copy(
            isLoggedIn = false,
            userName = null,
            userId = null
        )
        
        // 切换到 guest 用户并刷新列表
        repository.setUserId(MainRepository.GUEST_USER_ID)
        loadConversations()
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
     * 注意：现在历史对话列表由 HistoryViewModel 管理，这个方法主要用于计算知识点数量
     */
    fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val conversations = repository.getConversations()
                // 计算知识点数量（基于加载到的对话列表）
                val knowledgePoints = calculateKnowledgePointsFromList(conversations)
                _uiState.value = _uiState.value.copy(
                    knowledgePointCount = knowledgePoints,
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
     * 同时更新 ChatRepositoryExample 中的当前模型
     */
    fun selectModel(model: AIModel) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
        // 同步更新 ChatRepositoryExample 中的模型
        ChatRepositoryExample.getInstance(context).setCurrentModel(model)
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
                // 设置当前选中的对话ID
                _uiState.value = _uiState.value.copy(selectedConversationId = conversationId)
                // 触发导航事件
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
     * 注意：现在搜索功能由 HistoryViewModel 管理，这个方法保留用于兼容性
     */
    @Deprecated("搜索功能已由 HistoryViewModel 管理", ReplaceWith("historyViewModel.updateSearchQuery(query)"))
    fun updateSearchQuery(query: String) {
        // 已废弃，搜索功能由 HistoryViewModel 管理
    }

    /**
     * 删除对话
     * 注意：现在删除功能由 HistoryViewModel 管理，这个方法保留用于兼容性
     */
    @Deprecated("删除功能已由 HistoryViewModel 管理", ReplaceWith("historyViewModel.deleteConversation(conversationId)"))
    fun deleteConversation(conversationId: String) {
        // 已废弃，删除功能由 HistoryViewModel 管理
    }

    /**
     * 重命名对话
     * 注意：现在重命名功能由 HistoryViewModel 管理，这个方法保留用于兼容性
     */
    @Deprecated("重命名功能已由 HistoryViewModel 管理", ReplaceWith("historyViewModel.renameConversation(conversationId, newTitle)"))
    fun renameConversation(conversationId: String, newTitle: String) {
        // 已废弃，重命名功能由 HistoryViewModel 管理
    }

    /**
     * 切换置顶状态
     * 注意：现在置顶功能由 HistoryViewModel 管理，这个方法保留用于兼容性
     */
    @Deprecated("置顶功能已由 HistoryViewModel 管理", ReplaceWith("historyViewModel.togglePinConversation(conversationId)"))
    fun togglePinConversation(conversationId: String) {
        // 已废弃，置顶功能由 HistoryViewModel 管理
    }

    /**
     * 删除所有 guest 用户的临时对话（用于应用关闭时清理）
     */
    fun deleteGuestConversations() {
        viewModelScope.launch {
            try {
                repository.deleteConversationsByUserId(MainRepository.GUEST_USER_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 计算知识点数量（基于用户的历史对话和消息数）
     * 规则：每条对话算 100 个基础知识点，每条消息额外算 50 个
     */
    private suspend fun calculateKnowledgePoints(): Int {
        val conversations = repository.getConversations()
        return calculateKnowledgePointsFromList(conversations)
    }

    /**
     * 从对话列表计算知识点数量
     * 规则：每条对话算 100 个基础知识点，每条消息额外算 50 个
     */
    private fun calculateKnowledgePointsFromList(conversations: List<Conversation>): Int {
        var total = 0
        conversations.forEach { conv ->
            total += 100 // 基础知识点
            total += conv.messageCount * 50 // 每条消息额外知识点
        }
        return total
    }

    /**
     * 加载推荐话题
     * 根据用户最近10条历史对话标题，调用大模型生成4个推荐问题
     */
    fun loadRecommendations() {
        // 未登录不加载推荐
        if (!_uiState.value.isLoggedIn) {
            _uiState.value = _uiState.value.copy(
                recommendedTopics = emptyList(),
                knowledgePointCount = 0
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRecommendations = true)

            try {
                // 获取最近10条对话标题
                val conversations = repository.getConversations()
                    .sortedByDescending { it.lastMessageTime }
                    .take(10)
                    .map { it.title }

                // 计算知识点数量
                val knowledgePoints = calculateKnowledgePoints()
                
                if (conversations.isEmpty()) {
                    // 没有历史记录，使用默认推荐
                    _uiState.value = _uiState.value.copy(
                        recommendedTopics = getDefaultRecommendations(),
                        knowledgePointCount = knowledgePoints,
                        isLoadingRecommendations = false
                    )
                    return@launch
                }

                // 构建 prompt
                val historyTitles = conversations.joinToString("\n") { "- $it" }
                val prompt = """基于用户最近的搜索历史，总结出2个相关主题，并为每个主题生成2个推荐问题（共4个问题）。

用户最近的搜索历史：
$historyTitles

请严格按以下 JSON 格式返回（不要有其他文字）：
[
  {"theme": "主题1", "content": "推荐问题1"},
  {"theme": "主题1", "content": "推荐问题2"},
  {"theme": "主题2", "content": "推荐问题3"},
  {"theme": "主题2", "content": "推荐问题4"}
]

要求：
1. 问题应该简洁，不超过20个字
2. 问题应该与用户历史搜索内容相关但有拓展
3. 只返回JSON数组，不要有其他说明文字"""

                // 调用当前选中的模型 API
                val result = AiHelper.chatWithModel(_uiState.value.selectedModel, prompt)
                
                val topics = if (result.isSuccess) {
                    parseRecommendations(result.getOrNull() ?: "")
                } else {
                    getDefaultRecommendations()
                }

                _uiState.value = _uiState.value.copy(
                    recommendedTopics = topics,
                    knowledgePointCount = knowledgePoints,
                    isLoadingRecommendations = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    recommendedTopics = getDefaultRecommendations(),
                    knowledgePointCount = calculateKnowledgePoints(),
                    isLoadingRecommendations = false
                )
            }
        }
    }

    /**
     * 解析推荐话题 JSON
     */
    private fun parseRecommendations(json: String): List<RecommendedTopic> {
        return try {
            // 提取 JSON 数组部分
            val jsonArray = json.trim().let {
                val start = it.indexOf('[')
                val end = it.lastIndexOf(']')
                if (start >= 0 && end > start) it.substring(start, end + 1) else it
            }

            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val list: List<Map<String, String>> = gson.fromJson(jsonArray, type)

            list.take(4).map { item ->
                RecommendedTopic(
                    content = item["content"] ?: "推荐问题",
                    theme = item["theme"] ?: ""
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getDefaultRecommendations()
        }
    }

    /**
     * 获取默认推荐话题
     */
    private fun getDefaultRecommendations(): List<RecommendedTopic> {
        return listOf(
            RecommendedTopic("如何提高工作效率？", "效率提升"),
            RecommendedTopic("有哪些实用的学习方法？", "学习技巧"),
            RecommendedTopic("怎样保持健康的生活方式？", "健康生活"),
            RecommendedTopic("如何进行有效的时间管理？", "时间管理")
        )
    }

    /**
     * 点击推荐话题，直接发送到输入框
     */
    fun onRecommendationClick(topic: RecommendedTopic): String {
        return topic.content
    }
}

/**
 * 推荐话题数据类
 */
data class RecommendedTopic(
    val content: String,
    val theme: String = ""
)

/**
 * 主界面UI状态
 * 注意：历史对话列表现在由 HistoryViewModel 管理，这里不再包含 conversations 和 searchQuery
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedModel: AIModel = AIModels.defaultModel,
    val isWebSearchEnabled: Boolean = false,
    val isTextInputMode: Boolean = true,
    val selectedConversationId: String? = null,
    val isLoggedIn: Boolean = false, // 是否已登录
    val userName: String? = null, // 当前登录用户名
    val userId: String? = null, // 当前登录用户ID
    val knowledgePointCount: Int = 0, // 知识点数量（基于历史对话）
    val recommendedTopics: List<RecommendedTopic> = emptyList(), // 推荐话题列表
    val isLoadingRecommendations: Boolean = false // 是否正在加载推荐
)