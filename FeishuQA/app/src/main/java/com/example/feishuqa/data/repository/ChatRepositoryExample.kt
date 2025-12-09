package com.example.feishuqa.data.repository

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.feishuqa.app.history.HistoryModel
import com.example.feishuqa.common.utils.AiHelper
import com.example.feishuqa.common.utils.ImageUtils
import com.example.feishuqa.common.utils.JsonUtils
import com.example.feishuqa.common.utils.SessionManager
import com.example.feishuqa.data.entity.AIModel
import com.example.feishuqa.data.entity.AIModels
import com.example.feishuqa.data.entity.Message
import com.example.feishuqa.data.entity.MessageType
import com.example.feishuqa.data.entity.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.also
import kotlin.collections.find


// 单例 Repository
class ChatRepositoryExample private constructor(private val context: Context) {
    
    // 对话列表刷新回调接口
    interface OnConversationRefreshListener {
        fun onConversationListRefreshNeeded()
    }
    
    // 【新增】消息发送监听器接口
    interface OnMessageSendListener {
        fun onMessageSend()
        fun onAiMessageAdded()  // 新增：AI消息添加完成回调
    }
    
    private var refreshListener: OnConversationRefreshListener? = null
    private var messageSendListener: OnMessageSendListener? = null

    private val _messages = MutableLiveData<MutableList<Message>>(mutableListOf())
    val messages: LiveData<MutableList<Message>> = _messages
    
    // 当前对话ID
    private var currentConversationId: String? = null
    
    // 当前用户ID（未登录时使用 guest）
    private var currentUserId: String = MainRepository.GUEST_USER_ID
    
    // 当前选中的AI模型
    private var currentModel: AIModel = AIModels.defaultModel
    
    // 使用 HistoryModel 统一管理消息存储（新结构）
    private val historyModel = HistoryModel(context)

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
    
    init {
        // 不再在初始化时加载，等待设置对话ID后加载
    }
    
    /**
     * 设置当前对话ID，并加载对应的消息
     */
    fun setCurrentConversation(conversationId: String) {
        if (currentConversationId != conversationId) {
            currentConversationId = conversationId
            loadChatHistory()
        }
    }
    
    /**
     * 设置当前用户ID
     */
    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }
    
    /**
     * 设置当前选中的AI模型
     */
    fun setCurrentModel(model: AIModel) {
        currentModel = model
    }
    
    /**
     * 获取当前选中的AI模型
     */
    fun getCurrentModel(): AIModel {
        return currentModel
    }
    
    fun setOnConversationRefreshListener(listener: OnConversationRefreshListener?) {
        refreshListener = listener
    }
    
    // 【新增】设置消息发送监听器
    fun setOnMessageSendListener(listener: OnMessageSendListener?) {
        messageSendListener = listener
    }
    
    /**
     * 获取当前用户ID
     */
    private fun getCurrentUserId(): String {
        return SessionManager.getUserId(context) ?: MainRepository.GUEST_USER_ID
    }
    
    /**
     * 清除当前对话
     */
    fun clearCurrentConversation() {
        currentConversationId = null
        _messages.postValue(mutableListOf())
    }
    
    /**
     * 自动创建新对话（当没有选中对话时）
     * @param initialTitle 初始标题，默认为第一条消息内容
     * @return 新创建的对话ID
     */
    private fun createNewConversation(initialTitle: String = "新对话"): String {
        val userId = getCurrentUserId()
        val conversationIndex = historyModel.createConversation(userId, initialTitle)
        return conversationIndex.conversationId
    }

    // 核心业务：发送消息（包含压缩、上传模拟、状态流转）
    fun sendMessage(text: String, uri: Uri?) {
        val msgId = UUID.randomUUID().toString()
        
        // 自动创建新对话：如果没有选中对话，自动创建新对话 ★★★
        var conversationId = currentConversationId
        if (conversationId == null) {
            // 使用消息内容作为初始标题，如果内容为空则使用默认标题
            val initialTitle = if (text.isNotBlank()) {
                if (text.length > 20) "${text.take(20)}..." else text
            } else {
                "新对话"
            }
            conversationId = createNewConversation(initialTitle)
            currentConversationId = conversationId
            // 加载新创建对话的历史消息（此时应该为空）
            loadChatHistory()
            // 通知需要刷新对话列表
            refreshListener?.onConversationListRefreshNeeded()
        }

        // 1. 立即展示 loading 状态
        val userMsg = Message(
            id = msgId,
            conversationId = conversationId,
            senderId = "user",
            type = if (uri != null) MessageType.IMAGE else MessageType.TEXT,
            content = text,
            extraInfo = uri?.toString(), // 先暂时用 Uri 显示预览
            timestamp = System.currentTimeMillis(),
            status = if (uri != null) MessageStatus.SENDING else MessageStatus.SENT
        )
        addMessageInternal(userMsg)

        // 【新增】通知消息发送监听器，用户消息已发送
        messageSendListener?.onMessageSend()

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
                updateMessageStatus(msgId, finalPath, MessageStatus.SENT)
            }

            // 3. 模拟 AI 流式回复
            val aiMsgId = UUID.randomUUID().toString()
            val aiMsg = Message(
                id = aiMsgId,
                conversationId = conversationId,
                senderId = "ai",
                type = MessageType.TEXT,
                content = "", // 初始内容为空
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENDING // 初始状态为发送中
            )
            
            withContext(Dispatchers.Main) { addMessageInternal(aiMsg) }
            
            // 【移除】AI消息添加完成，通知监听器 - 移到流式回复开始前
            // messageSendListener?.onAiMessageAdded()
            
            // 4. 流式输出 AI 回复
            var fullContent = ""
            
            // 【新增】流式回复开始前，先滚动到底部确保能看到回复过程
            messageSendListener?.onAiMessageAdded()
            
            streamAiResponse(text, conversationId).collect { partialContent ->
                fullContent = partialContent
                // 更新消息内容（逐字显示）
                updateMessageContent(aiMsgId, fullContent)
            }
            
            // 5. 更新消息状态为已发送，并保存到新结构
            updateMessageStatus(aiMsgId, null, MessageStatus.SENT)
            
            // 6. 确保AI回复保存到新结构（updateMessageStatus 会调用，但这里再确认一次）
            withContext(Dispatchers.Main) {
                val list = _messages.value ?: return@withContext
                val aiMessage = list.find { it.id == aiMsgId }
                aiMessage?.let {
                    saveMessageToHistoryModel(it.copy(content = fullContent))
                }
            }
        }
    }

    private fun addMessageInternal(msg: Message) {
        val list = _messages.value ?: mutableListOf()
        list.add(msg)
        _messages.postValue(list)
        // 如果消息状态是已发送，保存到新结构
        // 注意：SENDING 状态的消息会在状态更新为 SENT 时再保存
        if (msg.status == MessageStatus.SENT) {
            saveMessageToHistoryModel(msg)
        }
    }

    private suspend fun updateMessageStatus(id: String, realPath: String?, status: MessageStatus) {
        withContext(Dispatchers.Main) {
            val list = _messages.value ?: return@withContext
            val targetIndex = list.indexOfFirst { it.id == id }
            if (targetIndex != -1) {
                val target = list[targetIndex]
                val updatedMessage = target.copy(
                    status = status,
                    extraInfo = realPath ?: target.extraInfo
                )
                list[targetIndex] = updatedMessage
                _messages.value = list // 触发刷新
                // 如果状态变为已发送，保存到新结构
                if (status == MessageStatus.SENT) {
                    saveMessageToHistoryModel(updatedMessage)
                }
            }
        }
    }
    
    private suspend fun updateMessageContent(id: String, content: String) {
        withContext(Dispatchers.Main) {
            val list = _messages.value ?: return@withContext
            val targetIndex = list.indexOfFirst { it.id == id }
            if (targetIndex != -1) {
                val target = list[targetIndex]
                // 优化：只在内容真正变化时更新，避免不必要的刷新
                if (target.content != content) {
                    val updatedMessage = target.copy(content = content)
                    list[targetIndex] = updatedMessage
                    
                    // 特殊处理：如果是表格内容，延迟更新以减少抖动
                    if (content.contains("|") && content.contains("\n")) {
                        kotlinx.coroutines.delay(50) // 表格内容延迟50ms更新
                    }
                    
                    _messages.value = list // 触发刷新
                }
            }
        }
    }
    
    /**
     * 加载本地聊天历史
     * 使用新结构（HistoryModel）加载消息
     */
    private fun loadChatHistory() {
        val conversationId = currentConversationId ?: return
        
        try {
            // 从新结构加载消息（使用 HistoryModel）
            val messageDetails = historyModel.getAllMessages(conversationId)
            
            // 将 MessageDetail 转换为 Message
            val loadedMessages = messageDetails.map { detail ->
                // 如果有 imageUrl，则消息类型为图片
                val messageType = if (detail.imageUrl != null) MessageType.IMAGE else MessageType.TEXT
                
                Message(
                    id = detail.messageId,
                    conversationId = conversationId,
                    senderId = if (detail.senderType == 0) "user" else "ai",
                    type = messageType,
                    content = detail.content,
                    extraInfo = detail.imageUrl, // 图片URL存储在 extraInfo 中
                    timestamp = detail.timestamp,
                    status = MessageStatus.SENT // 已保存的消息都是已发送状态
                )
            }
            
            // 按时间戳排序（最新的在最后）
            val sortedMessages = loadedMessages.sortedBy { it.timestamp }
            _messages.postValue(sortedMessages.toMutableList())
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果加载失败，保持空列表
            _messages.postValue(mutableListOf())
        }
    }
    
    /**
     * 保存消息到新结构（HistoryModel）
     * 只保存已发送的消息，避免保存中间状态（SENDING、TYPING等）
     * 
     * 注意：HistoryModel.addMessage() 会生成新的 messageId，所以这里通过内容+时间戳来判断是否重复
     */
    private fun saveMessageToHistoryModel(message: Message) {
        val conversationId = currentConversationId ?: return
        
        // 只保存已发送的消息
        if (message.status != MessageStatus.SENT) {
            return
        }
        
        try {
            // 检查消息是否已经存在于新结构中（通过内容和时间戳判断，避免重复保存）
            val existingMessages = historyModel.getAllMessages(conversationId)
            val isDuplicate = existingMessages.any { 
                it.content == message.content && 
                Math.abs(it.timestamp - message.timestamp) < 1000 // 1秒内的相同内容视为重复
            }
            
            if (isDuplicate) {
                // 消息已存在，不重复保存
                return
            }
            
            // 判断是用户消息还是AI消息
            val isUser = message.senderId == "user"
            
            // 提取 imageUrl（如果消息类型是图片，从 extraInfo 中获取）
            val imageUrl = if (message.type == MessageType.IMAGE) {
                message.extraInfo // 图片消息的 extraInfo 存储图片URL
            } else {
                null
            }
            
            // 使用 HistoryModel 保存消息
            // 传递 messageId 和 timestamp 以保持ID一致性
            historyModel.addMessage(
                conversationId = conversationId,
                content = message.content,
                isUser = isUser,
                messageId = message.id,
                timestamp = message.timestamp,
                imageUrl = imageUrl
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 清空当前对话的聊天历史
     * 注意：现在使用新结构，消息存储在 HistoryModel 中
     * 如果需要清空，应该通过 HistoryModel 删除对话，而不是只清空消息
     */
    fun clearChatHistory() {
        val conversationId = currentConversationId ?: return
        
        try {
            // 清空内存中的消息
            _messages.postValue(mutableListOf())
            // 注意：不再删除旧结构的文件，因为现在使用新结构
            // 如果需要删除对话，应该调用 HistoryModel.deleteConversation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * AI 流式回复 (打字机效果)
     * 根据当前选中的模型调用对应的 API
     * 如果 API 调用失败（网络问题等），先显示错误信息，再回退到模拟回复
     */
    private fun streamAiResponse(userQuery: String, conversationId: String): Flow<String> = flow {
        // 显示思考中状态
        emit("正在思考中...")
        
        // 调用 AI API 获取回复，失败时先显示错误再回退到模拟回复
        val fullResponse = try {
            val result = AiHelper.chatWithModel(currentModel, userQuery)
            if (result.isSuccess) {
                result.getOrNull() ?: mockAiResponse(userQuery) // 内容为空也使用模拟回复
            } else {
                // API调用失败，先显示错误信息，再显示模拟回复
                val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
                android.util.Log.w("ChatRepository", "API调用失败: $errorMsg")
                buildErrorWithFallbackResponse(errorMsg, userQuery)
            }
        } catch (e: Exception) {
            // 发生异常（网络问题等），先显示错误信息，再显示模拟回复
            android.util.Log.w("ChatRepository", "发生异常: ${e.message}")
            buildErrorWithFallbackResponse(e.message ?: "未知异常", userQuery)
        }
        
        val stringBuilder = StringBuilder()

        // 优化：减少更新频率，避免过于频繁的UI刷新
        var lastEmitTime = System.currentTimeMillis()
        val minEmitInterval = 50L // 最小发射间隔50ms
        
        // 特殊处理：检测是否包含表格，如果是则使用更长的间隔
        val containsTable = fullResponse.contains("|") && fullResponse.contains("\n")
        val tableEmitInterval = if (containsTable) 200L else minEmitInterval // 表格内容使用200ms间隔

        // 模拟逐字输出，速度随机变化
        for (char in fullResponse) {
            delay((15 + Math.random() * 25).toLong()) // 15-40ms随机延迟（加快显示速度）
            stringBuilder.append(char)
            
            val currentTime = System.currentTimeMillis()
            val currentEmitInterval = if (stringBuilder.toString().contains("|") && stringBuilder.toString().contains("\n")) {
                tableEmitInterval // 一旦开始表格内容，使用更长间隔
            } else {
                minEmitInterval
            }
            
            // 只在间隔足够大时发射，避免过于频繁的更新
            if (currentTime - lastEmitTime >= currentEmitInterval) {
                emit(stringBuilder.toString())
                lastEmitTime = currentTime
            }
        }
        
        // 确保最后的内容被发射
        if (stringBuilder.isNotEmpty()) {
            emit(stringBuilder.toString())
        }
    }

    /**
     * 构建包含错误信息和模拟回复的完整响应
     * 先显示错误信息方便定位问题，再显示模拟回复保证展示效果
     */
    private fun buildErrorWithFallbackResponse(errorMsg: String, query: String): String {
        return """⚠️ **API调用失败**
> $errorMsg

---

**以下为模拟回复：**

${mockAiResponse(query)}"""
    }

    private fun mockAiResponse(query: String): String {
        return when {
            query.contains("你好", ignoreCase = true) || query.contains("hi", ignoreCase = true) -> """
                你好！我是一个知识问答助手，很高兴为你提供帮助。
                
                我可以协助你解答各种问题，包括：
                - **知识查询**：提供准确的信息和解释
                - **代码示例**：分享编程相关的代码片段
                - **文档整理**：帮助你整理和总结内容
                - **日常疑问**：回答各种实用问题
                
                AI 基于你有权限的资料生成，数据保密仅你可见。
                有什么我可以帮助你的吗？
            """.trimIndent()
            
            query.contains("代码", ignoreCase = true) || query.contains("kotlin", ignoreCase = true) -> """
                好的！这是一个 Kotlin 的 Compose 示例：

                ```kotlin
                @Composable
                fun Greeting(name: String) {
                    Text(
                        text = "Hello, ${'$'}name!",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                ```

                这个组件展示了 Jetpack Compose 的基本用法：
                - **@Composable**：标记可组合函数
                - **参数传递**：通过函数参数传递数据
                - **样式设置**：使用 Modifier 和主题样式
                - **文本显示**：使用 Text 组件显示内容

                你可以这样调用它：
                ```kotlin
                Greeting(name = "World")
                ```
            """.trimIndent()
            
            query.contains("表格", ignoreCase = true) || query.contains("table", ignoreCase = true) -> """
                下面是一个 Markdown 表格示例：

                | 功能 | 描述 | 状态 |
                |------|------|------|
                | 文本消息 | 支持富文本显示 | ✅ 已完成 |
                | 打字机效果 | AI回复逐字显示 | ✅ 已完成 |
                | Markdown解析 | 支持标题、代码块、表格等 | ✅ 已完成 |
                | 历史记录 | 支持消息持久化 | ✅ 已完成 |
                | 文件上传 | 支持附件发送 | 🚧 开发中 |
                | 语音输入 | 支持语音转文字 | 📋 规划中 |

                这个表格展示了当前应用的功能状态。
            """.trimIndent()
            
            query.contains("标题", ignoreCase = true) || query.contains("heading", ignoreCase = true) -> """
                # 一级标题
                ## 二级标题
                ### 三级标题
                #### 四级标题
                
                这是不同级别的 Markdown 标题示例。
                
                ## 功能特性
                
                ### 核心功能
                - **打字机效果**：AI回复逐字显示
                - **Markdown支持**：完整解析各种格式
                - **历史记录**：消息持久化存储
                
                ### 技术特点
                - 基于 **MVVM** 架构
                - 使用 **Jetpack Compose**
                - 支持 **协程** 和 **Flow**
            """.trimIndent()
            
            else -> """我收到了你的消息："$query"

这是一个模拟的 AI 回复，展示了以下功能：

- **打字机效果**：你看到的内容正在逐字显示
- **Markdown支持**：支持 **粗体**、*斜体*、\`代码\` 等格式
- **实时响应**：模拟真实的 AI 对话体验

你可以尝试询问：
- "你好" - 查看自我介绍
- "代码" - 获取 Kotlin 示例
- "表格" - 查看表格渲染
- "标题" - 查看标题格式

有什么其他问题我可以帮助你解答吗？"""
        }
    }
}