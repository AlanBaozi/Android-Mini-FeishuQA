package com.example.feishuqa.data.entity

import kotlinx.serialization.Serializable

// 单条消息
@Serializable
data class MessageDetail
(
    val messageId: String,
    val content: String,
    val timestamp: Long,
    val senderType: Int, // 0=用户, 1=AI
    val messageOrder: Int,
    val imageUrl: String? = null // 图片URL
)

// 消息文件容器
@Serializable
data class MessageFile(
    val conversationId: String,
    val fileIndex: Int,
    var messageCount: Int,
    val messages: MutableList<MessageDetail> = mutableListOf()
)
