package com.example.feishuqa.data.repository

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.feishuqa.common.utils.ImageUtils
import com.example.feishuqa.data.entity.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.also
import kotlin.collections.find


// 单例 Repository
class ChatRepositoryExample private constructor(private val context: Context) {

    private val _messages = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val messages: LiveData<MutableList<ChatMessage>> = _messages

    companion object {
        @Volatile
        private var instance: ChatRepositoryExample? = null

        // 在 Application 或 MainActivity 初始化一次
        fun getInstance(context: Context): ChatRepositoryExample {
            return instance ?: synchronized(this) {
                instance ?: ChatRepositoryExample(context.applicationContext).also { instance = it }
            }
        }
    }

    // 核心业务：发送消息（包含压缩、上传模拟、状态流转）
    fun sendMessage(text: String, uri: Uri?) {
        val msgId = UUID.randomUUID().toString()

        // 1. 立即展示 loading 状态
        val userMsg = ChatMessage(
            id = msgId,
            content = text,
            localImagePath = uri?.toString(), // 先暂时用 Uri 显示预览
            isUser = true,
            time = System.currentTimeMillis(),
            isUploading = uri != null
        )
        addMessageInternal(userMsg)

        // 2. 后台处理
        CoroutineScope(Dispatchers.IO).launch {
            var finalPath: String? = null

            // 2.1 如果是图片，进行压缩
            if (uri != null) {
                // 【核心】在这里使用 context 调用压缩，ViewModel 不需要知道 context
                finalPath = ImageUtils.compressImage(context, uri)
                // 模拟网络上传耗时
                delay(1500)

                // 2.2 更新消息状态（压缩完成，替换为真实路径，Loading 结束）
                updateMessageStatus(msgId, finalPath, false)
            }

            // 3. 模拟 AI 回复
            delay(1000)
            val aiMsg = ChatMessage(
                id = msgId,
                content = text,
                localImagePath = uri?.toString(), // 先暂时用 Uri 显示预览
                isUser = false,
                time = System.currentTimeMillis(),
                isUploading = uri != null
            )
            withContext(Dispatchers.Main) { addMessageInternal(aiMsg) }
        }
    }

    private fun addMessageInternal(msg: ChatMessage) {
        val list = _messages.value ?: mutableListOf()
        list.add(msg)
        _messages.postValue(list)
    }

    private suspend fun updateMessageStatus(id: String, realPath: String?, isUploading: Boolean) {
        withContext(Dispatchers.Main) {
            val list = _messages.value ?: return@withContext
            val target = list.find { it.id == id }
            if (target != null) {
                target.isUploading = isUploading
                if (realPath != null) {
                    // 更新为压缩后的本地文件路径，加载更快
                    // 注意：这里需要 ChatMessage 是 data class 但 localImagePath 是 val
                    // 实际项目中建议用 copy()，这里为了演示简单直接修改 MutableList
                    // 如果 localImagePath 是 val，你需要: 
                    // val index = list.indexOf(target)
                    // list[index] = target.copy(localImagePath = realPath, isUploading = false)
                }
                _messages.value = list // 触发刷新
            }
        }
    }
}