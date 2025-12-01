package com.example.feishuqa.app.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feishuqa.data.entity.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainScreenViewModel() : ViewModel()
{
    private val model = MainScreenModel()
//    private val _conversationList = MutableLiveData<List<Conversation>>(emptyList())
//    val conversationList: LiveData<List<Conversation>> get() = _conversationList
//
//    private val userId = 1L
//
//    fun createConversation(title: String)
//    {
//        val finalTitle = if (title.isBlank()) "新对话" else title
//
//        val newConversation = model.createConversation(finalTitle, userId)
//
//        _conversationList.value = _conversationList.value!! + newConversation
//
//        // ⭐ 在 Logcat 打印结果，用于验证
//        Log.d("MainScreenVM", "创建对话：$newConversation")
//        Log.d("MainScreenVM", "当前对话数：${_conversationList.value?.size}")
//    }

    private val _navigateToConversation = MutableSharedFlow<String>(replay = 1)
    val navigateToConversation = _navigateToConversation.asSharedFlow()

    /**
     * 用户发送问题
     * 自动生成 conversationId = "conversation" + 时间戳
     */
    // 1. 去掉 suspend 关键字
    fun onSendQuestion(context: Context, title: String) {
        if (title.isBlank())
        {
            Log.d("TestLog", "标题为空，不执行")
            return
        }

        // 2. 在 ViewModel 内部启动协程
        viewModelScope.launch {
            Log.d("TestLog", "1. 协程启动，准备处理...")
            try
            {
                val conversationId = "conversation${System.currentTimeMillis()}"

                // ⭐ 关键：切换到 IO 线程写文件，解决卡顿
                withContext(Dispatchers.IO)
                {
                    model.createConversation(context, conversationId, title)
                    Log.d("TestLog", "2. 文件写入完成 (IO线程)")
                }

                // ⭐ 发送事件 (Channel)
                Log.d("TestLog", "3. 准备发送事件: $conversationId")
                _navigateToConversation.emit(conversationId)
                Log.d("TestLog", "4. 事件已发送")

            }
            catch (e: Exception)
            {
                Log.e("TestLog", "❌ 发生错误: ${e.message}", e)
            }
        }
    }
}
