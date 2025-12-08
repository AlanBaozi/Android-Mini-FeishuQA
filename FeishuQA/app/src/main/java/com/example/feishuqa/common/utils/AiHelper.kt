package com.example.feishuqa.common.utils

/**
 * deepseek/豆包 API工具类
 */

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object AiHelper
{
    private const val TAG = "AiHelper"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType() // JSON 类型
    private val gson = Gson() // JSON 解析器

    // 定义支持的模型类型
    enum class AiType
    {
        DEEPSEEK, // deepseek
        DOUBAO   // 豆包
    }

    // DeepSeek 配置
    private const val DEEPSEEK_API_KEY = "*"
    private const val DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"

    // 豆包配置
    private const val DOUBAO_API_KEY = "*" // 火山引擎 Key
    private const val DOUBAO_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
    private const val DOUBAO_ENDPOINT_ID = "ep-20251207164500-g58ww" // 模型接入点 ID

    // 内部配置数据类
    private data class AiConfig
        (
        val url: String,
        val apiKey: String,
        val modelName: String
    )

    // 配置 OkHttpClient
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 统一对话接口
     * @param type 模型类型 (AiType.DEEPSEEK 或 AiType.DOUBAO)
     * @param question 用户问题
     */
    suspend fun chat(type: AiType, question: String): Result<String>
    {
        // 1. 切换到 IO 线程执行网络请求，不阻塞 UI
        return withContext(Dispatchers.IO)
        {
            try
            {
                // 根据类型获取配置
                val config = when (type)
                {
                    // deepseek
                    AiType.DEEPSEEK -> AiConfig(
                        url = DEEPSEEK_URL,
                        apiKey = DEEPSEEK_API_KEY,
                        modelName = "deepseek-chat"
                    )
                    // 豆包
                    AiType.DOUBAO -> AiConfig(
                        url = DOUBAO_URL,
                        apiKey = DOUBAO_API_KEY,
                        modelName = DOUBAO_ENDPOINT_ID
                    )
                }
                Log.d(TAG, "正在调用: ${type.name} | URL: ${config.url}")

                // 构建请求数据
                val requestModel = ChatRequest(
                    model = config.modelName,
                    messages = listOf(Message(role = "user", content = question))
                )

                val jsonBody = gson.toJson(requestModel)

                val request = Request.Builder()
                    .url(config.url)
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .post(jsonBody.toRequestBody(JSON_TYPE))
                    .build()

                Log.d(TAG, "开始思考: $jsonBody")

                // 执行请求
                val response = client.newCall(request).execute()
                val responseString = response.body?.string()

                if (!response.isSuccessful || responseString == null)
                {
                    return@withContext Result.failure(Exception("请求失败 Code: ${response.code}, Body: $responseString"))
                }

                // 解析结果
                val chatResponse = gson.fromJson(responseString, ChatResponse::class.java)
                Log.d(TAG, "chat: $chatResponse")
                val aiReply = chatResponse.choices?.firstOrNull()?.message?.content

                if (!aiReply.isNullOrEmpty())
                {
                    Result.success(aiReply)
                }
                else
                {
                    Result.failure(Exception("回复内容为空"))
                }
            }
            catch (e: Exception)
            {
                Log.e(TAG, "发生错误: ${e.message}")
                Result.failure(e)
            }
        }
    }
}

// Response响应对象
data class ChatResponse
    (
    val id: String?,
    val model: String?,
    // 对应 JSON 中的 "choices"
    val choices: List<Choice>?
)

// 对应 choices 数组里的每一项
data class Choice
    (
    val index: Int?,
    // 对应 JSON 中的 "message": {...}
    val message: Message?,

    // 常用字段，代表为什么停止生成 (如 "stop" 代表正常结束)
    @SerializedName("finish_reason")
    val finishReason: String?
)

// 3. 对应 message 对象 (Request 和 Response 通用)
data class Message
    (
    val role: String,   // "user" 或 "assistant"
    val content: String // 对话的具体内容
)

// 请求体
data class ChatRequest
    (
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val stream: Boolean = false
)