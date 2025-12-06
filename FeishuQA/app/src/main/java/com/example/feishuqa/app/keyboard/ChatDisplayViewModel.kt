package com.example.feishuqa.app.keyboard

import androidx.lifecycle.ViewModel
import com.example.feishuqa.data.repository.ChatRepositoryExample

// --- ViewModel 1: 负责显示 ---
// --- Display ViewModel ---
class ChatDisplayViewModel(repository: ChatRepositoryExample) : ViewModel() {
    // 直接观察仓库
    val messages = repository.messages
}

