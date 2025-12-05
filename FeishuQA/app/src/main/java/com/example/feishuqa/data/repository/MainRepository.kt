package com.example.feishuqa.data.repository

import android.content.Context
import com.example.feishuqa.common.utils.JsonUtils
import com.example.feishuqa.data.entity.AIModel
import com.example.feishuqa.data.entity.AIModels
import com.example.feishuqa.data.entity.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 主界面数据仓库
 * Model层：负责主界面相关数据的获取和处理
 */
class MainRepository(private val context: Context) {

    /**
     * 获取历史对话列表
     */
    suspend fun getConversations(): List<Conversation> {
        return withContext(Dispatchers.IO) {
            try {
                val fileNames = JsonUtils.getAllConversationFiles(context)
                val conversations = mutableListOf<Conversation>()
                
                fileNames.forEach { fileName ->
                    try {
                        val conversation = loadConversationFromFile(fileName)
                        if (conversation != null) {
                            conversations.add(conversation)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                conversations.sortedByDescending { it.lastMessageTime }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 从文件加载对话
     */
    private fun loadConversationFromFile(fileName: String): Conversation? {
        return try {
            val content = JsonUtils.readJsonFromFiles(context, fileName)
            if (content.isBlank() || content == "[]") {
                return null
            }

            val json = JSONObject(content)
            val id = JsonUtils.extractIdFromFileName(fileName)
            val title = json.optString("title", "新对话")
            
            val lastMessage = if (json.has("messages") && !json.isNull("messages")) {
                val messagesArray = json.getJSONArray("messages")
                if (messagesArray.length() > 0) {
                    val lastMsg = messagesArray.getJSONObject(messagesArray.length() - 1)
                    val contentMsg = lastMsg.optString("content", "")
                    if (contentMsg.isNotEmpty()) contentMsg else null
                } else {
                    null
                }
            } else {
                null
            }
            
            val updatedTime = json.optLong("updatedTime", System.currentTimeMillis())
            val createdTime = json.optLong("createdTime", updatedTime)
            val messageCount = if (json.has("messages") && !json.isNull("messages")) {
                json.getJSONArray("messages").length()
            } else {
                0
            }
            
            val isPinned = json.optBoolean("isPinned", false)
            val userId = if (json.has("userId")) json.getInt("userId") else null
            
            Conversation(
                id = id,
                title = title,
                lastMessage = lastMessage,
                lastMessageTime = updatedTime,
                messageCount = messageCount,
                isPinned = isPinned,
                createdAt = createdTime,
                updatedTime = updatedTime,
                userId = userId
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建新对话
     */
    suspend fun createConversation(title: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val conversationId = "conversation${System.currentTimeMillis()}"
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
                    put("messages", org.json.JSONArray())
                }

                val fileName = "$conversationId.json"
                val file = java.io.File(context.filesDir, fileName)
                file.writeText(root.toString())
                
                Result.success(conversationId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 获取可用的AI模型列表
     */
    fun getAvailableModels(): List<AIModel> {
        return AIModels.defaultModels
    }

    /**
     * 获取默认模型
     */
    fun getDefaultModel(): AIModel {
        return AIModels.defaultModel
    }
}




