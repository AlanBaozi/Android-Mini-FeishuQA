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
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.feishuqa.R
import com.example.feishuqa.app.history.HistoryView
import com.example.feishuqa.app.history.HistoryViewModel
import com.example.feishuqa.app.keyboard.ChatInputView
import com.example.feishuqa.app.login.LoginActivity
import com.example.feishuqa.databinding.ActivityMainBinding
import com.example.feishuqa.databinding.LayoutDrawerBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * 主界面 View 层：UI展示和交互
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

    // 记录上次的登录状态
    private var lastLoggedInState: Boolean? = null

    fun init() {
        // 初始化侧边栏历史记录
        historyViewModel = ViewModelProvider(
            lifecycleOwner as ViewModelStoreOwner,
            HistoryViewModel.Factory(context)
        )[HistoryViewModel::class.java]

        historyView = HistoryView(
            context = context,
            drawerBinding = drawerBinding,
            viewModel = historyViewModel,
            lifecycleOwner = lifecycleOwner
        ).apply {
            onConversationClick = { conversationId -> viewModel.selectConversation(conversationId) }
            onCloseDrawer = { binding.drawerLayout.closeDrawer(GravityCompat.START) }
        }
        historyView.init()

        viewModel.refreshLoginState()

        setupTopBar()
        setupDrawer()
        setupRecommendations()
        observeViewModel()
        startLogoAnimation()
    }

    private fun setupRecommendations() {
        binding.gridRecommendations.visibility = View.GONE
        binding.tvWelcomeSubtitle.visibility = View.GONE
    }

    private fun setupTopBar() {
        binding.btnLogin.setOnClickListener {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }

        binding.layoutUserInfo.setOnClickListener { view -> showUserMenu(view) }

        binding.btnNewChat.setOnClickListener { viewModel.createNewConversation("新对话") }

        binding.btnMenu.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
    }

    private fun showUserMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            menu.add(0, 1, 0, "退出登录")
            setOnMenuItemClickListener { item ->
                if (item.itemId == 1) {
                    showLogoutConfirmDialog()
                    true
                } else false
            }
            show()
        }
    }

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

    private fun setupDrawer() {
        drawerBinding.btnNewConversation.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            viewModel.createNewConversation("新对话")
        }
        drawerBinding.layoutKnowledgeBase.setOnClickListener {
            Toast.makeText(context, "打开知识库", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                // UI 状态观察
                launch {
                    viewModel.uiState.collect { state ->
                        updateUI(state)

                        if (lastLoggedInState != state.isLoggedIn) {
                            lastLoggedInState = state.isLoggedIn
                            if (state.isLoggedIn && state.recommendedTopics.isEmpty() && !state.isLoadingRecommendations) {
                                viewModel.loadRecommendations()
                            }
                            historyView.setUserId(state.userId ?: "guest")
                        }
                    }
                }
                // 导航观察
                launch {
                    viewModel.navigateToConversation.collect { conversationId ->
                        conversationId?.let {
                            viewModel.selectConversation(it)
                            Toast.makeText(context, "已切换到新对话", Toast.LENGTH_SHORT).show()
                            viewModel.clearNavigation()
                        }
                    }
                }
                // 登录检查观察
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

    private fun showLoginRequiredDialog() {
        AlertDialog.Builder(context)
            .setTitle("提示")
            .setMessage("请先登录")
            .setPositiveButton("去登录") { _, _ ->
                context.startActivity(Intent(context, LoginActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateUI(state: MainUiState) {
        updateLoginUI(state)

        val userId = state.userId ?: "guest"
        historyView.setUserId(userId)
        historyViewModel.selectConversation(state.selectedConversationId ?: "")


        chatInputView.updateModelName(state.selectedModel.name)
        chatInputView.updateWebSearchState(state.isWebSearchEnabled)

        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    private fun updateLoginUI(state: MainUiState) {
        if (state.isLoggedIn && state.userName != null) {
            binding.btnLogin.visibility = View.GONE
            binding.layoutUserInfo.visibility = View.VISIBLE
            binding.tvUsername.text = state.userName
            binding.tvWelcomeTitle.text = "嗨！${state.userName}"
            binding.gridRecommendations.visibility = View.VISIBLE
            binding.tvWelcomeSubtitle.visibility = View.VISIBLE
            binding.tvWelcomeSubtitle.text = "整合你可访问的 ${formatNumber(state.knowledgePointCount)} 个知识点，AI 搜索生成回答"
            updateRecommendations(state.recommendedTopics)
        } else {
            binding.btnLogin.visibility = View.VISIBLE
            binding.layoutUserInfo.visibility = View.GONE
            binding.tvWelcomeTitle.text = "嗨！这里是飞书知识问答"
            binding.gridRecommendations.visibility = View.GONE
            binding.tvWelcomeSubtitle.visibility = View.GONE
        }
    }

    private fun updateRecommendations(topics: List<RecommendedTopic>) {
        if (topics.isEmpty()) return

        val textViews = listOf(binding.tvRecommend1, binding.tvRecommend2, binding.tvRecommend3, binding.tvRecommend4)
        val cards = listOf(binding.cardRecommend1, binding.cardRecommend2, binding.cardRecommend3, binding.cardRecommend4)

        topics.forEachIndexed { index, topic ->
            if (index < textViews.size) {
                textViews[index].text = topic.content
                cards[index].setOnClickListener {
                    onRecommendationClick(topic.content)
                }
            }
        }
    }

    private fun onRecommendationClick(content: String) {
        // ★★★ 核心修改：调用封装方法设置文本并弹起键盘 ★★★
        chatInputView.setInputTextAndFocus(content)
    }

    private fun formatNumber(number: Int): String {
        return NumberFormat.getNumberInstance(Locale.US).format(number)
    }

    fun showModelSelectorDialog() {
        val dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_model_selector, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.rg_models)
        val currentModel = viewModel.uiState.value.selectedModel

        // 对应 ID 映射 (确保 ID 存在于你的 dialog_model_selector.xml 中)
        val modelIdMap = mapOf(
            "deepseek-r1" to R.id.rb_deepseek,
            "gpt-4" to R.id.rb_gpt4,
            "claude-3.5" to R.id.rb_claude,
            "qwen" to R.id.rb_qwen,
            "doubao" to R.id.rb_doubao
        )

        modelIdMap[currentModel.id]?.let { radioGroup.check(it) }

        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val targetId = modelIdMap.entries.find { it.value == checkedId }?.key
            targetId?.let { id ->
                viewModel.getAvailableModels().find { it.id == id }?.let { model ->
                    viewModel.selectModel(model)
                }
            }
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun startLogoAnimation() {
        val rotateAnimation = RotateAnimation(
            0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 8000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.RESTART
        }
        binding.logoImage.startAnimation(rotateAnimation)
    }

    fun deleteGuestConversations() {
        historyViewModel.deleteConversationsByUserId("guest")
    }
}