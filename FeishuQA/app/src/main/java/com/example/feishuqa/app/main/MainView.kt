package com.example.feishuqa.app.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.PopupMenu
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.feishuqa.R
import com.example.feishuqa.MainActivity
import com.example.feishuqa.app.history.HistoryView
import com.example.feishuqa.app.history.HistoryViewModel
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

    private lateinit var historyView: HistoryView
    private lateinit var historyViewModel: HistoryViewModel



    /**
     * 初始化View
     */
    fun init() {
        // 初始化历史对话 ViewModel 和 View
        historyViewModel = ViewModelProvider(
            lifecycleOwner as androidx.lifecycle.ViewModelStoreOwner,
            HistoryViewModel.Factory(context)
        )[HistoryViewModel::class.java]
        
        historyView = HistoryView(
            context = context,
            drawerBinding = drawerBinding,
            viewModel = historyViewModel,
            lifecycleOwner = lifecycleOwner
        ).apply {
            onConversationClick = { conversationId ->
                viewModel.selectConversation(conversationId)
            }
            onCloseDrawer = {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        historyView.init()
        
        // 刷新登录状态
        viewModel.refreshLoginState()
        
        setupTopBar()
        setupDrawer()
        setupRecommendations()
        //setupInputBar() 已经被chatinputview内部接管
        observeViewModel()
        startLogoAnimation()
    }

    /**
     * 设置推荐话题的点击事件
     */
    private fun setupRecommendations() {
        // 初始时隐藏推荐区域
        binding.gridRecommendations.visibility = View.GONE
        binding.tvWelcomeSubtitle.visibility = View.GONE
    }

    /**
     * 设置顶部导航栏
     */
    private fun setupTopBar() {
        // 登录按钮
        binding.btnLogin.setOnClickListener {
            val intent = Intent(context, LoginActivity::class.java)
            context.startActivity(intent)
        }

        // 用户信息区域点击（显示退出登录菜单）
        binding.layoutUserInfo.setOnClickListener { view ->
            showUserMenu(view)
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
     * 显示用户菜单（退出登录选项）
     */
    private fun showUserMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "退出登录")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showLogoutConfirmDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * 显示退出登录确认对话框
     */
    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(context)
            .setTitle("退出登录")
            .setMessage("确定要退出当前账号吗？")
            .setPositiveButton("确定") { _, _ ->
                viewModel.logout()
                Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
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
    }



    // 记录上次的登录状态，用于检测登录状态变化
    private var lastLoggedInState: Boolean? = null

    /**
     * 观察ViewModel状态变化
     */
    private fun observeViewModel() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                // 观察UI状态
                launch {
                    viewModel.uiState.collect { state ->
                        updateUI(state)
                        
                        // 检测登录状态变化，当用户登录后加载推荐
                        if (lastLoggedInState != state.isLoggedIn) {
                            lastLoggedInState = state.isLoggedIn
                            if (state.isLoggedIn && state.recommendedTopics.isEmpty() && !state.isLoadingRecommendations) {
                                viewModel.loadRecommendations()
                            }
                            // 更新历史对话 View 的用户ID
                            val userId = state.userId ?: "guest"
                            historyView.setUserId(userId)
                        }
                    }
                }

                // 观察导航事件
                launch {
                    viewModel.navigateToConversation.collect { conversationId ->
                        conversationId?.let {
                            // 切换到新对话
                            switchToConversation(it)
                            viewModel.clearNavigation()
                        }
                    }
                }

                // 观察需要登录事件
                launch {
                    viewModel.requireLogin.collect { requireLogin ->
                        if (requireLogin) {
                            showLoginRequiredDialog()
                            viewModel.clearRequireLogin()
                        }
                    }
                }
            }
        }
    }

    /**
     * 显示需要登录的提示对话框
     */
    private fun showLoginRequiredDialog() {
        AlertDialog.Builder(context)
            .setTitle("提示")
            .setMessage("请先登录")
            .setPositiveButton("去登录") { _, _ ->
                val intent = Intent(context, LoginActivity::class.java)
                context.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 更新UI
     */
    private fun updateUI(state: MainUiState) {
        // 更新登录状态显示
        updateLoginUI(state)
        
        // 更新历史对话 View 的用户ID和选中状态
        val userId = state.userId ?: "guest"
        historyView.setUserId(userId)
        historyViewModel.selectConversation(state.selectedConversationId ?: "")

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
     * 更新登录相关的UI
     */
    private fun updateLoginUI(state: MainUiState) {
        if (state.isLoggedIn && state.userName != null) {
            // 已登录：显示用户信息，隐藏登录按钮
            binding.btnLogin.visibility = View.GONE
            binding.layoutUserInfo.visibility = View.VISIBLE
            binding.tvUsername.text = state.userName
            
            // 更新欢迎词为"嗨！用户名"
            binding.tvWelcomeTitle.text = "嗨！${state.userName}"
            
            // 显示推荐话题区域
            binding.gridRecommendations.visibility = View.VISIBLE
            
            // 显示知识点数（始终显示，即使为0也显示）
            binding.tvWelcomeSubtitle.visibility = View.VISIBLE
            binding.tvWelcomeSubtitle.text = "整合你可访问的 ${formatNumber(state.knowledgePointCount)} 个知识点，AI 搜索生成回答"
            
            // 更新推荐话题内容
            updateRecommendations(state.recommendedTopics)
        } else {
            // 未登录：显示登录按钮，隐藏用户信息
            binding.btnLogin.visibility = View.VISIBLE
            binding.layoutUserInfo.visibility = View.GONE
            
            // 恢复默认欢迎词
            binding.tvWelcomeTitle.text = "嗨！这里是飞书知识问答"
            
            // 隐藏推荐话题区域和知识点数
            binding.gridRecommendations.visibility = View.GONE
            binding.tvWelcomeSubtitle.visibility = View.GONE
        }
    }

    /**
     * 更新推荐话题内容
     */
    private fun updateRecommendations(topics: List<RecommendedTopic>) {
        if (topics.isEmpty()) return

        val textViews = listOf(
            binding.tvRecommend1,
            binding.tvRecommend2,
            binding.tvRecommend3,
            binding.tvRecommend4
        )

        val cards = listOf(
            binding.cardRecommend1,
            binding.cardRecommend2,
            binding.cardRecommend3,
            binding.cardRecommend4
        )

        topics.forEachIndexed { index, topic ->
            if (index < textViews.size) {
                textViews[index].text = topic.content
                cards[index].setOnClickListener {
                    // 点击推荐话题，将内容填入输入框并发送
                    onRecommendationClick(topic.content)
                }
            }
        }
    }

    /**
     * 点击推荐话题的处理
     */
    private fun onRecommendationClick(content: String) {
        // 将推荐内容设置到输入框
        val etInput = chatInputView.findViewById<android.widget.EditText>(R.id.et_input)
        etInput?.setText(content)
        etInput?.setSelection(content.length)
        
        // 获取焦点并弹出键盘
        etInput?.requestFocus()
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * 格式化数字（添加千位分隔符）
     */
    private fun formatNumber(number: Int): String {
        return java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(number)
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
    
    /**
     * 切换到指定对话
     */
    private fun switchToConversation(conversationId: String) {
        // 设置当前选中的对话
        viewModel.selectConversation(conversationId)
        
        // 显示提示
        Toast.makeText(context, "已切换到新对话", Toast.LENGTH_SHORT).show()
    }

    /**
     * 删除 guest 用户的所有对话（用于应用关闭时清理）
     */
    fun deleteGuestConversations() {
        historyViewModel.deleteConversationsByUserId("guest")
    }
}