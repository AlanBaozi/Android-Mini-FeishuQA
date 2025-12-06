package com.example.feishuqa.app.main

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.feishuqa.R
import com.example.feishuqa.adapter.HistoryConversationAdapter
import com.example.feishuqa.app.keyboard.ChatInputView
import com.example.feishuqa.app.login.LoginActivity
import com.example.feishuqa.data.entity.AIModel
import com.example.feishuqa.databinding.ActivityMainBinding
import com.example.feishuqa.databinding.LayoutDrawerBinding
import com.example.feishuqa.databinding.LayoutInputBarBinding
import kotlinx.coroutines.launch

/**
 * 主界面View层
 * View层：负责UI展示和用户交互，不包含业务逻辑
 */
class MainView(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val drawerBinding: LayoutDrawerBinding,
    private val chatInputView: ChatInputView,
    private val viewModel: MainViewModel,
    private val lifecycleOwner: LifecycleOwner
) {

    private lateinit var historyAdapter: HistoryConversationAdapter



    /**
     * 初始化View
     */
    fun init() {
        // TODO: 从登录模块获取用户ID并设置
        // viewModel.setUserId(userId)
        
        setupTopBar()
        setupDrawer()
        //setupInputBar() 已经被chatinputview内部接管
        observeViewModel()
        startLogoAnimation()
    }

    /**
     * 设置顶部导航栏
     */
    private fun setupTopBar() {
        // 登录按钮
        binding.btnLogin.setOnClickListener {
            val intent = android.content.Intent(context, LoginActivity::class.java)
            context.startActivity(intent)
        }

        // 新建对话按钮
        binding.btnNewChat.setOnClickListener {
            viewModel.createNewConversation("新对话")
        }

        // 菜单按钮（打开侧边栏）
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    /**
     * 设置侧边栏
     */
    private fun setupDrawer() {
        // 新建对话按钮
        drawerBinding.btnNewConversation.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            viewModel.createNewConversation("新对话")
        }

        // 知识库入口
        drawerBinding.layoutKnowledgeBase.setOnClickListener {
            Toast.makeText(context, "打开知识库", Toast.LENGTH_SHORT).show()
        }

        // 设置历史对话列表
        setupHistoryList()
    }

    /**
     * 设置历史对话列表
     */
    private fun setupHistoryList() {
        historyAdapter = HistoryConversationAdapter(
            onItemClick = { conversation ->
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                viewModel.selectConversation(conversation.id)
            },
            onDeleteClick = { conversationId ->
                viewModel.deleteConversation(conversationId)
            },
            onRenameClick = { conversationId, currentTitle ->
                showRenameDialog(conversationId, currentTitle)
            },
            onPinClick = { conversationId, isPinned ->
                viewModel.togglePinConversation(conversationId)
            }
        )

        drawerBinding.rvHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }

        // 设置搜索框
        drawerBinding.etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSearchQuery(s?.toString() ?: "")
            }
        })
    }

    /**
     * 显示重命名对话框
     */
    private fun showRenameDialog(conversationId: String, currentTitle: String) {
        val input = android.widget.EditText(context)
        input.setText(currentTitle)
        input.selectAll()

        AlertDialog.Builder(context)
            .setTitle("重命名对话")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    viewModel.renameConversation(conversationId, newTitle)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }



    /**
     * 观察ViewModel状态变化
     */
    private fun observeViewModel() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                // 观察UI状态
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }

                // 观察导航事件
                viewModel.navigateToConversation.collect { conversationId ->
                    conversationId?.let {
                        Toast.makeText(context, "打开对话: $it", Toast.LENGTH_SHORT).show()
                        viewModel.clearNavigation()
                    }
                }
            }
        }
    }

    /**
     * 更新UI
     */
    private fun updateUI(state: MainUiState) {
        // 更新对话列表（使用过滤后的列表）
        val filteredConversations = state.getFilteredConversations()
        historyAdapter.submitList(filteredConversations)
        historyAdapter.setSelectedConversation(state.selectedConversationId)
        
        if (filteredConversations.isEmpty() && !state.isLoading) {
            drawerBinding.rvHistory.visibility = View.GONE
            drawerBinding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            drawerBinding.rvHistory.visibility = View.VISIBLE
            drawerBinding.layoutEmptyState.visibility = View.GONE
        }

        // 更新 ChatInputView 上的状态显示 (模型名称、联网高亮)
        val tvSelectedModel = chatInputView.findViewById<android.widget.TextView>(R.id.tv_selected_model)
        val btnWebSearch = chatInputView.findViewById<android.view.View>(R.id.btn_web_search)
        val tvWebSearch = chatInputView.findViewById<android.widget.TextView>(R.id.tv_web_search)
        val icWebSearch = chatInputView.findViewById<android.widget.ImageView>(R.id.ic_web_search)

        // 安全判空，防止ID改了导致崩溃
        tvSelectedModel?.text = state.selectedModel.name

        if (state.isWebSearchEnabled) {
            btnWebSearch?.setBackgroundResource(R.drawable.bg_chip_selected)
            tvWebSearch?.setTextColor(context.getColor(R.color.feishu_blue))
            icWebSearch?.setColorFilter(context.getColor(R.color.feishu_blue))
        } else {
            btnWebSearch?.setBackgroundResource(R.drawable.bg_chip_normal)
            tvWebSearch?.setTextColor(context.getColor(R.color.text_secondary))
            icWebSearch?.setColorFilter(context.getColor(R.color.text_secondary))
        }

        // 显示错误
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    /**
     * 显示模型选择对话框
     */
    fun showModelSelectorDialog() {
        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_model_selector, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.rg_models)
        val currentModel = viewModel.uiState.value.selectedModel

        // 设置当前选中的模型
        when (currentModel.id) {
            "deepseek-r1" -> radioGroup.check(R.id.rb_deepseek)
            "gpt-4" -> radioGroup.check(R.id.rb_gpt4)
            "claude-3.5" -> radioGroup.check(R.id.rb_claude)
            "qwen" -> radioGroup.check(R.id.rb_qwen)
            "doubao" -> radioGroup.check(R.id.rb_doubao)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        // 监听选择变化
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val model = when (checkedId) {
                R.id.rb_deepseek -> viewModel.getAvailableModels().find { it.id == "deepseek-r1" }
                R.id.rb_gpt4 -> viewModel.getAvailableModels().find { it.id == "gpt-4" }
                R.id.rb_claude -> viewModel.getAvailableModels().find { it.id == "claude-3.5" }
                R.id.rb_qwen -> viewModel.getAvailableModels().find { it.id == "qwen" }
                R.id.rb_doubao -> viewModel.getAvailableModels().find { it.id == "doubao" }
                else -> null
            }
            model?.let {
                viewModel.selectModel(it)
            }
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    /**
     * 启动Logo旋转动画
     */
    private fun startLogoAnimation() {
        val rotateAnimation = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 8000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.RESTART
        }
        binding.logoImage.startAnimation(rotateAnimation)
    }
}

