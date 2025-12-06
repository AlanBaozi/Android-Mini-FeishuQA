package com.example.feishuqa.app.keyboard

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.feishuqa.data.repository.ChatRepositoryExample

// --- ViewModel 2: 负责输入 ---
class ChatInputViewModel(private val repository: ChatRepositoryExample) : ViewModel() {

    private val _pendingImageUri = MutableLiveData<Uri?>()
    val pendingImageUri: LiveData<Uri?> = _pendingImageUri

    fun selectImage(uri: Uri) { _pendingImageUri.value = uri }
    fun clearPendingImage() { _pendingImageUri.value = null }

    fun sendMessage(text: String) {
        val uri = _pendingImageUri.value
        if (text.isEmpty() && uri == null) return

        // ViewModel 只管发令，不处理 Context，不处理压缩
        repository.sendMessage(text, uri)

        clearPendingImage()
    }
}