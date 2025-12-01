package com.example.feishuqa.app.main

import android.content.Context
import com.example.feishuqa.data.entity.Conversation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

//class MainScreenModel
//{

//    private val conversations = mutableListOf<Conversation>()
//    private var idCounter = 1L   // 自增ID
//
//    // 模拟创建会话
//    fun createConversation(title: String, userId: Long): Conversation
//    {
//        val now = System.currentTimeMillis()
//
//        val conversation = Conversation(
//            conversationId = idCounter++,
//            title = if (title.isBlank()) "新对话" else title,
//            createTime = now,
//            updateTime = now,
//            isPinned = false,
//            isDeleted = false,
//            userId = userId
//        )
//
//        conversations.add(conversation)
//        return conversation
//    }



//}

class MainScreenModel
{

    /**
     * 创建对话文件
     * @param conversationId 唯一ID，例如 "conversation1700000000000"
     * @param title 对话标题
     */
    fun createConversation(context: Context, conversationId: String, title: String)
    {

        val meta = JSONObject().apply {
            put("conversationId", conversationId)
            put("title", title)
            put("createTime", System.currentTimeMillis())
            put("updateTime", System.currentTimeMillis())
            put("isPinned", false)
            put("isDeleted", false)
        }

        val root = JSONObject().apply {
            put("conversationMeta", meta)
            put("messages", JSONArray())
        }

        val fileName = "$conversationId.json"
        val file = File(context.filesDir, fileName)
        file.writeText(root.toString())
    }
}
