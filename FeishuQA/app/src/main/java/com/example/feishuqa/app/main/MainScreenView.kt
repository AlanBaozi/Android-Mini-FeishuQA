package com.example.feishuqa.app.main

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch


@Composable
fun MainScreenView(
    viewModel: MainScreenViewModel = viewModel()
) {
    var inputText by remember { mutableStateOf("") }

    val context = LocalContext.current

    // 监听 ViewModel 发出的 conversationId
    LaunchedEffect(Unit) {
        Log.d("TestLog", "UI: 开始监听事件...") // 打印到 Run 窗口
        viewModel.navigateToConversation.collect { conversationId ->
            // 收到事件！
            Log.d("TestLog","UI: 🔥🔥🔥 终于收到了! ID: $conversationId")
            // 这里执行跳转...
        }
    }
    Column(modifier = Modifier.padding(16.dp)) {

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("输入对话标题") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                viewModel.onSendQuestion(context, inputText)
                inputText = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("创建新对话（输出到 Logcat）")
        }
    }
}

