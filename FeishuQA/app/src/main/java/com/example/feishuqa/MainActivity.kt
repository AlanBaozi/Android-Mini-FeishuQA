package com.example.feishuqa

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.feishuqa.app.chat.ChatAdapter
import com.example.feishuqa.app.chat.ChatViewModel
import kotlinx.coroutines.launch

/**
 * 主聊天界面Activity
 * 负责消息列表的展示和用户交互
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter
    
    // Views
    private lateinit var recyclerView: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnVoice: ImageView
    private lateinit var layoutWelcome: LinearLayout
    private lateinit var btnBack: View
    
    // 输入法管理器
    private lateinit var inputMethodManager: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initViews()
        initListeners()
        observeData()
    }

    private fun initViews() {
        // 初始化视图
        recyclerView = findViewById(R.id.recyclerView)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnVoice = findViewById(R.id.btnVoice)
        layoutWelcome = findViewById(R.id.layoutWelcome)
        btnBack = findViewById(R.id.btnBack)

        // 初始化输入法管理器
        inputMethodManager = getSystemService<InputMethodManager>()!!

        // 设置RecyclerView
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        val layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true // 列表倒序，从底部开始
            stackFromEnd = true
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        
        // 添加滚动监听器，优化用户体验
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // 可以在这里添加分页加载逻辑
            }
        })
    }

    private fun initListeners() {
        // 返回按钮
        btnBack.setOnClickListener { finish() }

        // 输入框文本变化监听器
        etInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                btnSend.isVisible = hasText
                btnVoice.isVisible = !hasText
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 发送按钮点击事件
        btnSend.setOnClickListener {
            sendMessage()
        }

        // 输入框回车发送
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun sendMessage() {
        val content = etInput.text.toString().trim()
        if (content.isNotEmpty()) {
            viewModel.sendTextMessage(content)
            etInput.setText("")
            
            // 隐藏软键盘
            inputMethodManager.hideSoftInputFromWindow(etInput.windowToken, 0)
            
            // 滚动到最新消息
            recyclerView.post {
                if (adapter.itemCount > 0) {
                    recyclerView.scrollToPosition(0)
                }
            }
        } else {
            // 显示空消息提示
            Toast.makeText(this, "请输入消息内容", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 1. 更新消息列表
                    updateMessageList(state.messages, state.isTyping)

                    // 2. 更新UI状态
                    updateUiState(state.messages.isEmpty(), state.error)
                }
            }
        }
    }

    private fun updateMessageList(messages: List<com.example.feishuqa.data.entity.Message>, isTyping: Boolean) {
        adapter.submitList(messages) {
            // 滚动到最新消息（当有新消息或AI正在输入时）
            if ((isTyping || messages.isNotEmpty()) && adapter.itemCount > 0) {
                recyclerView.post {
                    recyclerView.scrollToPosition(0)
                }
            }
        }
    }

    private fun updateUiState(isEmpty: Boolean, error: String?) {
        // 更新空状态显示
        layoutWelcome.isVisible = isEmpty
        recyclerView.isVisible = !isEmpty
        
        // 显示错误信息
        error?.let {
            Toast.makeText(this, "错误: $it", Toast.LENGTH_LONG).show()
        }
    }
}