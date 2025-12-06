package com.example.feishuqa.app.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.feishuqa.data.repository.ChatRepositoryExample
import kotlin.jvm.java

// --- 通用工厂类 (用于注入 Repository) ---
class ChatViewModelFactory(private val repository: ChatRepositoryExample) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatInputViewModel::class.java)) {
            return ChatInputViewModel(repository) as T
        }
        if (modelClass.isAssignableFrom(ChatDisplayViewModel::class.java)) {
            return ChatDisplayViewModel(repository) as T
        }
        throw kotlin.IllegalArgumentException("Unknown ViewModel class")
    }
}