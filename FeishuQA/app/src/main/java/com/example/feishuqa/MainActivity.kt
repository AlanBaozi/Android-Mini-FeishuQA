package com.example.feishuqa

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.feishuqa.app.chat.ChatScreen
import com.example.feishuqa.app.main.MainScreenView
import com.example.feishuqa.common.utils.JsonUtils
import com.example.feishuqa.common.utils.theme.FeishuQATheme
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val fileName = "conversation.json"

        // 创建一个新的 conversation JSON 对象
        val newConversation = JSONObject().apply {
            put("conversationId", 2)
            put("title", "第二个话题")
            put("userId", 1001)
            put("createdTime", System.currentTimeMillis())
            put("updatedTime", System.currentTimeMillis())
            put("messages", org.json.JSONArray()) // 空消息数组
        }

        // 调用 appendJsonObject 写入内部存储
        val success = JsonUtils.overwriteJsonObject(this, fileName, newConversation)
        Log.d("JsonTest", "写入结果: $success")

        // 读取文件内容
        val content = JsonUtils.readJsonFromFiles(this, fileName)
        Log.d("JsonTest", "文件内容: $content")
        setContent {
            FeishuQATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 直接展示聊天界面
                    ChatScreen(
                        onBackClick = { finish() }
                    )
                    //MainScreenView()
                }
            }
        }
    }
}