package com.example.feishuqa.data.entity

data class ChatMessage(
    val id: String,
    val content: String,      // 文字内容
    val localImagePath: String?, // 本地图片路径 (压缩后)
    val isUser: Boolean,      // true: 用户, false: AI
    val time: Long,
    // 【新增】是否正在发送/加载中 (默认 false，只有用户新发的图片默认为 true)
    var isUploading: Boolean = false
)