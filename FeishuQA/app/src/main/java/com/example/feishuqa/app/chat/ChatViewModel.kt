package com.example.feishuqa.app.chat

import androidx.lifecycle.ViewModel
import com.example.feishuqa.data.repository.ChatRepositoryExample

// --- ViewModel 1: 负责显示 ---
// --- Display ViewModel ---
class ChatDisplayViewModel(private val repository: ChatRepositoryExample) : ViewModel() {
    // 直接观察仓库
    val messages = repository.messages
    
    /**
     * 设置当前对话并加载对应的消息
     */
    fun setCurrentConversation(conversationId: String) {
        repository.setCurrentConversation(conversationId)
    }
}