package com.example.feishuqa.data.entity

/**
 * 对话表数据类，对应 user.json 文件结构
 */

data class Conversation
(
    val conversationId: Long,
    val title: String,
    val createTime: Long,
    val updateTime: Long,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val userId: Long
)
