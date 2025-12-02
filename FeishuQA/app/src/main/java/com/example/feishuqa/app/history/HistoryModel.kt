package com.example.feishuqa.app.history

import com.example.feishuqa.data.entity.Conversation

/**
 * 历史对话列表的 UI 状态
 */
data class HistoryUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedConversationId: String? = null,
    val error: String? = null
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
