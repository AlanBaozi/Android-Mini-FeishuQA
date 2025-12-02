package com.example.feishuqa.data.entity

/**
 * 对话实体类
 */
data class Conversation(
    val id: String,  // 对话 ID（时间戳）
    val title: String,
    val lastMessage: String? = null,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedTime: Long = System.currentTimeMillis(),
    val userId: Int? = null
) {
    fun getDisplayTitle(): String {
        return if (title.isBlank()) "新对话" else title
    }
}
