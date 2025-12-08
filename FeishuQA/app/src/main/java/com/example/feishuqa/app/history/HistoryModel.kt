package com.example.feishuqa.app.history

import android.content.Context
import com.example.feishuqa.common.utils.JsonUtils
import com.example.feishuqa.data.entity.Conversation
import com.example.feishuqa.data.entity.ConversationDetail
import com.example.feishuqa.data.entity.ConversationIndex
import com.example.feishuqa.data.entity.ConversationIndexFile
import com.example.feishuqa.data.entity.MessageDetail
import com.example.feishuqa.data.entity.MessageFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 历史对话列表的 UI 状态
 */
data class HistoryUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedConversationId: String? = null,
    val error: String? = null
) {
    /**
     * 获取过滤后的对话列表（根据搜索关键词和置顶状态排序）
     */
    fun getFilteredConversations(): List<Conversation> {
        val filtered = if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.lastMessage?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        
        // 置顶的排在前面，然后按时间倒序
        return filtered.sortedWith(
            compareByDescending<Conversation> { it.isPinned }
                .thenByDescending { it.lastMessageTime }
        )
    }
}

/**
 * 历史对话 Model 层
 * 职责：
 * 1. 负责所有与会话历史相关的数据访问和业务逻辑
 * 2. 桥接 JsonUtils 进行底层存储
 * 3. 将 ConversationIndex 转换为 Conversation（用于 UI 显示）
 */
class HistoryModel(private val context: Context) {

    private val jsonFormatter = Json {
        ignoreUnknownKeys = true // 即使JSON里有多余字段也不报错
        prettyPrint = true       // 生成的JSON格式好看
        encodeDefaults = true    // 默认值也会写入JSON
    }

    private val INDEX_FILE_PATH = "index/conversations.json"
    private val MESSAGES_PER_FILE = 100

    /**
     * 获取指定用户的会话列表（转换为 Conversation 用于 UI 显示）
     * @param userId 当前登录用户的ID
     * @return 该用户的会话列表（已转换为 Conversation 对象，包含最后一条消息）
     */
    fun getAllConversations(userId: String): List<Conversation> {
        val indexList = getAllConversationIndexes(userId)
        
        // 将 ConversationIndex 转换为 Conversation
        return indexList.mapNotNull { index ->
            convertIndexToConversation(index)
        }
    }

    /**
     * 获取指定用户的会话索引列表（内部方法）
     */
    private fun getAllConversationIndexes(userId: String): List<ConversationIndex> {
        val jsonContent = JsonUtils.readJsonFromFiles(context, INDEX_FILE_PATH)

        // 判空处理
        if (jsonContent.isBlank() || jsonContent == "[]") {
            return emptyList()
        }

        return try {
            // 1. 解码：将整个 JSON 字符串直接转换为 ConversationIndexFile 对象
            val indexFile = jsonFormatter.decodeFromString<ConversationIndexFile>(jsonContent)

            // 2. 过滤和排序 (使用 Kotlin 集合操作，代码简洁高效)
            indexFile.conversations
                // 过滤：排除已删除的 (!it.isDeleted) 且只保留当前用户的 (it.userId == userId)
                .filter { !it.isDeleted && it.userId == userId }
                // 排序：先按置顶降序，再按更新时间降序
                .sortedWith(
                    compareByDescending<ConversationIndex> { it.isPinned }
                        .thenByDescending { it.updateTime }
                )
        } catch (e: Exception) {
            // 解析失败，返回空列表
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 将 ConversationIndex 转换为 Conversation（用于 UI 显示）
     * 包括获取最后一条消息和消息数量
     */
    private fun convertIndexToConversation(index: ConversationIndex): Conversation? {
        return try {
            // 1. 获取最后一条消息和消息数量
            val messages = getAllMessages(index.conversationId)
            val lastMessage = messages.lastOrNull()?.content
            val messageCount = messages.size

            // 2. 转换为 Conversation 对象
            Conversation(
                id = index.conversationId,
                title = index.title,
                lastMessage = lastMessage,
                lastMessageTime = index.updateTime,
                messageCount = messageCount,
                isPinned = index.isPinned,
                createdAt = index.createTime,
                updatedTime = index.updateTime,
                userId = index.userId.toIntOrNull()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果转换失败，至少返回基本信息
            Conversation(
                id = index.conversationId,
                title = index.title,
                lastMessage = null,
                lastMessageTime = index.updateTime,
                messageCount = 0,
                isPinned = index.isPinned,
                createdAt = index.createTime,
                updatedTime = index.updateTime,
                userId = index.userId.toIntOrNull()
            )
        }
    }

    /**
     * 获取指定会话的所有消息内容
     * 职责：循环读取 messages/{convId}/ 目录下所有分页文件，并合并消息
     *
     * @param conversationId 要获取消息的会话ID
     * @return List<MessageDetail> 包含所有已排序的消息列表
     */
    fun getAllMessages(conversationId: String): List<MessageDetail> {
        // 1. 获取会话索引信息，确保目录路径是已知的
        val index = getConversationIndexById(conversationId) ?: return emptyList()

        val allMessages = mutableListOf<MessageDetail>()
        var fileIndex = 1

        // 2. 分页读取循环
        while (true) {
            // 构造分页文件名 (例如: messages/conversation_1764992458418/messages_001.json)
            val fileName = "${index.messageDir}/messages_${fileIndex.toString().padStart(3, '0')}.json"

            // 使用 JsonUtils 读取文件内容
            val jsonString = JsonUtils.readJsonFromFiles(context, fileName)

            // 如果返回 "[]" 或空，说明读到头了，停止循环
            if (jsonString == "[]" || jsonString.isBlank()) break

            try {
                // 3. 反序列化为 MessageFile 对象
                // MessageFile 包含了这一批消息 (1-100 或 101-200)
                val messageFile = jsonFormatter.decodeFromString<MessageFile>(jsonString)

                // 将读取到的消息加入总列表
                allMessages.addAll(messageFile.messages)

                // 准备读取下一页
                fileIndex++
            } catch (e: Exception) {
                // 文件解析失败，通常意味着数据损坏，中断读取
                e.printStackTrace()
                break
            }
        }

        // 4. 按 messageOrder 排序返回
        return allMessages.sortedBy { it.messageOrder }
    }

    /**
     * 根据 conversationId 获取会话详情
     * @param conversationId String  要查找的会话的唯一标识 ID
     * @return ConversationDetail?   如果成功找到并解析，返回该会话的详情对象；
     * 如果会话不存在、已被删除或文件读取失败，则返回 null
     */
    fun getConversationDetail(conversationId: String): ConversationDetail? {
        // 第一步：去查索引表
        val index = getConversationIndexById(conversationId) ?: return null

        // 第二步：根据查到的路径，去读取真正的详情文件
        return loadConversationDetail(index.dataFile)
    }

    /**
     * 更新会话标题
     * 职责：修改索引文件和详情文件中的标题和更新时间
     *
     * @param conversationId 要修改的会话ID
     * @param newTitle 新的会话标题
     */
    fun updateConversationTitle(conversationId: String, newTitle: String) {
        val now = System.currentTimeMillis()

        // 1. 更新索引文件 (Index File)
        updateConversationIndex(conversationId) { index ->
            index.title = newTitle
            index.updateTime = now
        }

        // 2. 同时更新详情文件 (Detail File)
        getConversationDetail(conversationId)?.let { detail ->
            // 路径格式固定为: conversations/{ID}.json
            val detailFilePath = "conversations/${detail.conversationId}.json"

            // 使用 data class 的 copy 方法创建新对象并修改字段
            val updatedDetail = detail.copy(
                title = newTitle,
                updateTime = now
            )

            // 写入详情文件，使用重建的路径
            saveObjectToJsonFile(detailFilePath, updatedDetail)
        }
    }

    /**
     * 软删除会话
     * 职责：在会话索引文件中将 isDeleted 标志设为 true，从而将该会话从列表中隐藏
     * 详情文件和消息文件保留在磁盘上，以便将来可能恢复
     */
    fun deleteConversation(conversationId: String) {
        // 调用 updateConversationIndex，传入修改逻辑
        updateConversationIndex(conversationId) { index ->
            // 将 isDeleted 设为 true
            index.isDeleted = true
            // 同时更新时间戳
            index.updateTime = System.currentTimeMillis()
        }
    }

    /**
     * 删除指定用户的所有对话（包括索引、详情文件和消息文件）
     * 用于清理未登录用户的临时对话数据
     * @param userId 要删除的用户ID（例如 "guest"）
     */
    fun deleteConversationsByUserId(userId: String) {
        try {
            // 1. 读取索引文件，找到所有该用户的对话
            val jsonString = JsonUtils.readJsonFromFiles(context, INDEX_FILE_PATH)
            if (jsonString.isBlank() || jsonString == "[]") {
                return // 没有数据，直接返回
            }

            val indexFile = jsonFormatter.decodeFromString<ConversationIndexFile>(jsonString)
            val conversationsToDelete = indexFile.conversations.filter { it.userId == userId }

            if (conversationsToDelete.isEmpty()) {
                return // 没有该用户的对话，直接返回
            }

            // 2. 删除每个对话的相关文件
            conversationsToDelete.forEach { index ->
                // 删除详情文件
                JsonUtils.deleteJSONFile(context, index.dataFile)

                // 删除消息目录下的所有文件
                val messageDir = File(context.filesDir, index.messageDir)
                if (messageDir.exists() && messageDir.isDirectory) {
                    messageDir.listFiles()?.forEach { file ->
                        file.delete()
                    }
                    messageDir.delete() // 删除空目录
                }
            }

            // 3. 从索引文件中移除这些对话
            val remainingConversations = indexFile.conversations.filter { it.userId != userId }.toMutableList()
            val updatedIndexFile = indexFile.copy(
                conversations = remainingConversations,
                lastUpdateTime = System.currentTimeMillis()
            )

            // 4. 保存更新后的索引文件
            saveObjectToJsonFile(INDEX_FILE_PATH, updatedIndexFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 切换会话置顶状态
     * @param conversationId 会话ID
     * @param isPinned 是否置顶
     */
    fun togglePinConversation(conversationId: String, isPinned: Boolean) {
        updateConversationIndex(conversationId) { index ->
            index.isPinned = isPinned
            index.updateTime = System.currentTimeMillis()
        }

        // 同时更新详情文件
        getConversationDetail(conversationId)?.let { detail ->
            val detailFilePath = "conversations/${detail.conversationId}.json"
            val updatedDetail = detail.copy(
                isPinned = isPinned,
                updateTime = System.currentTimeMillis()
            )
            saveObjectToJsonFile(detailFilePath, updatedDetail)
        }
    }

    /**
     * 创建一个新的会话实体，并将其添加到索引文件中
     * @param userId 当前登录用户的ID
     * @param initialTitle 会话的初始标题（通常是用户的第一句话或默认值）
     * @return 新创建的 ConversationIndex 实体
     */
    fun createConversation(userId: String, initialTitle: String = "新对话"): ConversationIndex {
        // 1. 生成必要信息
        val now = System.currentTimeMillis()
        val conversationId = generateId("conversation") // 使用辅助函数生成唯一ID

        // 2. 创建 ConversationIndex 实体
        val newIndex = ConversationIndex(
            conversationId = conversationId,
            userId = userId,
            title = initialTitle,
            createTime = now,
            updateTime = now,
            isPinned = false,
            isDeleted = false,
            // 详情文件和消息目录路径
            dataFile = "conversations/$conversationId.json",
            messageDir = "messages/$conversationId"
        )

        // 3. 读取现有索引或创建新的索引容器
        val jsonContent = JsonUtils.readJsonFromFiles(context, INDEX_FILE_PATH)

        // 定义索引文件容器 (对应文档中的 ConversationIndexFile 结构)
        var indexFileObj: JSONObject
        var conversationsArray: JSONArray

        try {
            if (jsonContent.isBlank() || jsonContent == "[]") {
                // 文件为空或不存在，创建新的索引容器和空数组
                indexFileObj = JSONObject()
                conversationsArray = JSONArray()
                indexFileObj.put("version", "1.0")
            } else {
                // 文件存在，解析现有内容
                indexFileObj = JSONObject(jsonContent)
                conversationsArray = indexFileObj.optJSONArray("conversations") ?: JSONArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 解析失败，保险起见创建新的
            indexFileObj = JSONObject()
            conversationsArray = JSONArray()
            indexFileObj.put("version", "1.0")
        }

        // 4. 将新会话转换为 JSONObject 并追加
        val newIndexJson = JSONObject()
        newIndexJson.put("conversationId", newIndex.conversationId)
        newIndexJson.put("userId", newIndex.userId)
        newIndexJson.put("title", newIndex.title)
        newIndexJson.put("createTime", newIndex.createTime)
        newIndexJson.put("updateTime", newIndex.updateTime)
        newIndexJson.put("isPinned", newIndex.isPinned)
        newIndexJson.put("isDeleted", newIndex.isDeleted)
        newIndexJson.put("dataFile", newIndex.dataFile)
        newIndexJson.put("messageDir", newIndex.messageDir)

        conversationsArray.put(newIndexJson)

        // 5. 更新元数据并覆盖写入整个文件
        indexFileObj.put("lastUpdateTime", now)
        indexFileObj.put("conversations", conversationsArray)

        // 使用工具类写入文件
        JsonUtils.overwriteJsonObject(context, INDEX_FILE_PATH, indexFileObj)

        // 6. 构建详情文件的 JSONObject
        val detailJson = JSONObject()
        detailJson.put("conversationId", newIndex.conversationId)
        detailJson.put("userId", userId)
        detailJson.put("title", newIndex.title)
        detailJson.put("createTime", newIndex.createTime)
        detailJson.put("updateTime", newIndex.updateTime)
        detailJson.put("isPinned", newIndex.isPinned)
        detailJson.put("modelType", "gpt-3.5") // 默认模型，文档要求
        detailJson.put("messageDir", newIndex.messageDir)

        // 7. 写入详情文件
        JsonUtils.overwriteJsonObject(context, newIndex.dataFile, detailJson)

        return newIndex
    }

    /**
     * 添加一条消息（用户发送 或 AI回复）
     * 职责：计算分页 -> 写入消息文件 -> 更新会话索引
     * @param conversationId 会话ID
     * @param content 消息内容
     * @param isUser 是否为用户消息
     * @param messageId 消息ID（可选，如果不提供则自动生成）
     * @param timestamp 时间戳（可选，如果不提供则使用当前时间）
     * @param imageUrl 图片URL（可选）
     * @return 新创建的消息
     */
    fun addMessage(
        conversationId: String, 
        content: String, 
        isUser: Boolean,
        messageId: String? = null,
        timestamp: Long? = null,
        imageUrl: String? = null
    ): MessageDetail {
        val now = timestamp ?: System.currentTimeMillis()
        val msgId = messageId ?: "msg_${now}_${(1000..9999).random()}"

        // 1. 计算 Order 和 FileIndex
        val allMessages = getAllMessages(conversationId)
        val messageOrder = allMessages.size + 1 // 消息序号：总数 + 1

        // 计算当前消息应该存入哪个文件
        // 规则：(Order - 1) / 100 + 1
        val fileIndex = (messageOrder - 1) / MESSAGES_PER_FILE + 1

        // 2. 构建消息实体
        val newMessage = MessageDetail(
            messageId = msgId,
            content = content,
            timestamp = now,
            senderType = if (isUser) 0 else 1, // 0=用户, 1=AI
            messageOrder = messageOrder,
            imageUrl = imageUrl
        )

        // 3. 准备写入路径
        val index = getConversationIndexById(conversationId)
            ?: throw IllegalStateException("会话ID不存在或已被软删除")

        // 路径: messages/conv_xxx/messages_001.json
        val fileName = "${index.messageDir}/messages_${fileIndex.toString().padStart(3, '0')}.json"

        // 4. 读取该分页文件 (如果存在) 或 创建新的 MessageFile
        val jsonString = JsonUtils.readJsonFromFiles(context, fileName)

        val messageFile = if (jsonString != "[]" && jsonString.isNotBlank()) {
            try {
                // 解析旧文件内容
                jsonFormatter.decodeFromString<MessageFile>(jsonString)
            } catch (e: Exception) {
                // 解析失败则新建
                MessageFile(conversationId, fileIndex, 0)
            }
        } else {
            // 文件不存在则新建
            MessageFile(conversationId, fileIndex, 0)
        }

        // 5. 添加消息并保存
        messageFile.messages.add(newMessage)
        messageFile.messageCount = messageFile.messages.size

        // 写入消息文件
        saveObjectToJsonFile(fileName, messageFile)

        // 6. 更新索引 (Index File)
        // 更新 updateTime 和 title
        updateIndexFile { indexFile ->
            val target = indexFile.conversations.find { it.conversationId == conversationId }
            if (target != null) {
                target.updateTime = now

                // 如果是第一条消息，设置会话标题
                if (messageOrder == 1) {
                    target.title = if (content.length > 20) "${content.take(20)}..." else content
                }
            }
        }
        return newMessage
    }

    // ==================== 辅助函数 ====================

    /**
     * 生成conversationId
     */
    private fun generateId(prefix: String): String {
        // 使用 System.currentTimeMillis() 作为主要ID
        val timestamp = System.currentTimeMillis()
        return "${prefix}_$timestamp"
    }

    /**
     * 辅助：索引文件更新的通用封装
     * 职责：读取整个索引文件，执行修改操作，并覆盖写回磁盘
     */
    private fun updateIndexFile(action: (ConversationIndexFile) -> Unit) {
        // 1. 读取 JSON 字符串
        val jsonString = JsonUtils.readJsonFromFiles(context, INDEX_FILE_PATH)

        // 2. 解码整个文件结构
        val indexFile = if (jsonString.isBlank() || jsonString == "[]") {
            ConversationIndexFile()
        } else {
            try {
                jsonFormatter.decodeFromString<ConversationIndexFile>(jsonString)
            } catch (e: Exception) {
                // 解析失败时，返回一个空的结构，避免程序崩溃
                ConversationIndexFile()
            }
        }

        // 3. 应用外部传入的修改逻辑 (action lambda)
        action(indexFile)

        // 4. 更新元数据并编码写入
        val updatedFile = indexFile.copy(lastUpdateTime = System.currentTimeMillis())

        // 使用桥接函数写入文件
        saveObjectToJsonFile(INDEX_FILE_PATH, updatedFile)
    }

    /**
     * 核心：查找并更新索引文件中的目标项
     * 职责：在索引列表中找到 conversationId 对应的 ConversationIndex，并应用 updater
     */
    private fun updateConversationIndex(conversationId: String, updater: (ConversationIndex) -> Unit) {
        // 调用 updateIndexFile，并传入具体的查找和修改逻辑
        updateIndexFile { indexFile ->
            // 查找目标项
            indexFile.conversations.find { it.conversationId == conversationId }?.let { target ->
                // 对找到的目标项应用外部修改（例如修改 title 和 updateTime）
                updater(target)
            }
        }
    }

    /**
     * 辅助：从文件加载 ConversationDetail
     */
    private fun loadConversationDetail(fileName: String): ConversationDetail? {
        // 1. 调用工具类 JsonUtils 读取文件内容
        val jsonString = JsonUtils.readJsonFromFiles(context, fileName)

        // 2. 如果是空或者 "[]"，直接返回 null
        if (jsonString == "[]" || jsonString.isBlank())
            return null

        return try {
            // 3. 反序列化
            jsonFormatter.decodeFromString<ConversationDetail>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 辅助：根据 ID 查找会话索引
     */
    private fun getConversationIndexById(conversationId: String): ConversationIndex? {
        // 1. 读取总索引文件 (index/conversations.json)
        val jsonString = JsonUtils.readJsonFromFiles(context, INDEX_FILE_PATH)

        if (jsonString == "[]" || jsonString.isBlank())
            return null

        return try {
            // 2. 将整个大 JSON 字符串转为 ConversationIndexFile 对象
            val indexFile = jsonFormatter.decodeFromString<ConversationIndexFile>(jsonString)

            // 3. 在列表中找到一个 item，ID相同且并且没有被标记为删除
            indexFile.conversations.find { it.conversationId == conversationId && !it.isDeleted }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 核心桥接函数：Kotlin对象 -> JSONObject -> JsonUtils
     */
    private inline fun <reified T> saveObjectToJsonFile(fileName: String, obj: T) {
        try {
            val jsonString = jsonFormatter.encodeToString(obj)
            val jsonObject = JSONObject(jsonString)
            JsonUtils.overwriteJsonObject(context, fileName, jsonObject)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
