package com.example.feishuqa

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.feishuqa.app.login.LoginActivity
import com.example.feishuqa.app.main.MainView
import com.example.feishuqa.app.main.MainViewModel
import com.example.feishuqa.app.main.MainViewModelFactory
import com.example.feishuqa.common.utils.SessionManager
import com.example.feishuqa.data.repository.MainRepository
import com.example.feishuqa.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * 主界面Activity
 * Activity层：只负责初始化和绑定View、ViewModel
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var mainView: MainView

    // 【新增 1】 定义 Repository 和 Factory (用于 ChatInputView)
    // 如果你项目里已经有了单例模式，可以直接调用，这里为了保险起见写全
    internal val chatViewModelFactory by lazy {
        com.example.feishuqa.app.keyboard.ChatViewModelFactory(
            com.example.feishuqa.data.repository.ChatRepositoryExample.getInstance(applicationContext)
        )
    }

    // 【新增 2】 注册权限和相册回调
    private val permissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) android.widget.Toast.makeText(this, "需要录音权限", android.widget.Toast.LENGTH_SHORT).show()
    }

    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) binding.chatInputView.onImageSelected(uri)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 创建ViewModel
        viewModel = ViewModelProvider(this, MainViewModelFactory(this))[MainViewModel::class.java]

        // 初始化ChatRepositoryExample的用户ID和默认模型
        val chatRepository = com.example.feishuqa.data.repository.ChatRepositoryExample.getInstance(applicationContext)
        val userId = if (SessionManager.isLoggedIn(this)) {
            SessionManager.getUserId(this) ?: MainRepository.GUEST_USER_ID
        } else {
            MainRepository.GUEST_USER_ID
        }
        chatRepository.setCurrentUserId(userId)
        // 设置默认模型
        chatRepository.setCurrentModel(com.example.feishuqa.data.entity.AIModels.defaultModel)
        
        // 设置对话列表刷新监听器
        chatRepository.setOnConversationRefreshListener(object : com.example.feishuqa.data.repository.ChatRepositoryExample.OnConversationRefreshListener {
            override fun onConversationListRefreshNeeded() {
                // 当新对话创建时，刷新对话列表
                viewModel.loadConversations()
            }
        })

        binding.chatInputView.init(this, chatViewModelFactory)
        binding.chatInputView.setActionListener(object : com.example.feishuqa.app.keyboard.ChatInputView.ActionListener {
            override fun requestRecordAudioPermission(): Boolean {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) return true
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                return false
            }
            override fun openImagePicker() = pickImageLauncher.launch("image/*")

            // 这里把事件委托给 MainViewModel 或 MainView 处理
            override fun onWebSearchClick() = viewModel.toggleWebSearch()
            override fun onModelSelectClick() = mainView.showModelSelectorDialog()
            
            // 检查是否已登录
            override fun checkLoginBeforeSend(): Boolean {
                if (!SessionManager.isLoggedIn(this@MainActivity)) {
                    showLoginRequiredDialog()
                    return false
                }
                return true
            }
        })

        // 创建View并初始化
        val drawerBinding = com.example.feishuqa.databinding.LayoutDrawerBinding.bind(binding.navDrawer.root)

        
        mainView = MainView(this, binding, drawerBinding, binding.chatInputView, viewModel, this)
        mainView.init()

        // 初始化聊天显示组件
        initChatDisplay()

        // 监听导航事件，当创建新对话或选择对话时在主界面直接加载对话
        lifecycleScope.launch {
            viewModel.navigateToConversation.collect { conversationId ->
                conversationId?.let {
                    // 直接在主界面加载对话，不需要跳转到其他Activity
                    displayViewModel.setCurrentConversation(it)
                    viewModel.clearNavigation()
                }
            }
        }
    }

    /**
     * 处理返回键
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    /**
     * 显示需要登录的提示对话框
     */
    private fun showLoginRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage("请先登录")
            .setPositiveButton("去登录") { _, _ ->
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 初始化聊天显示组件
     */
    private fun initChatDisplay() {
        // 初始化ChatDisplayView
        binding.chatDisplayView.init(this, chatViewModelFactory)
        
        // 监听消息变化，控制欢迎区域和聊天区域的显示切换
        val displayViewModel = androidx.lifecycle.ViewModelProvider(this, chatViewModelFactory)
            .get(com.example.feishuqa.app.keyboard.ChatDisplayViewModel::class.java)
        
        displayViewModel.messages.observe(this) { messages ->
            if (messages.isNullOrEmpty()) {
                // 没有消息时显示欢迎区域
                binding.welcomeSection.visibility = android.view.View.VISIBLE
                binding.chatDisplayView.visibility = android.view.View.GONE
            } else {
                // 有消息时显示聊天区域
                binding.welcomeSection.visibility = android.view.View.GONE
                binding.chatDisplayView.visibility = android.view.View.VISIBLE
            }
        }
        
        // 保存displayViewModel实例用于导航事件
        this.displayViewModel = displayViewModel
        
        // 【新增】监听消息发送事件，当用户发送消息时主动滚动到底部
        val chatRepository = com.example.feishuqa.data.repository.ChatRepositoryExample.getInstance(applicationContext)
        chatRepository.setOnMessageSendListener(object : com.example.feishuqa.data.repository.ChatRepositoryExample.OnMessageSendListener {
            override fun onMessageSend() {
                // 用户发送消息时，滚动到用户消息位置（平滑动画）
                binding.chatDisplayView.scrollToBottom(smooth = true)
            }
            
            override fun onAiMessageAdded() {
                // AI消息添加完成时，强制滚动到底部，确保能看到AI回复
                // 使用较长延迟，确保：
                // 1. AI消息已经完全添加到列表
                // 2. 流式回复已经开始，用户能看到回复过程
                binding.chatDisplayView.postDelayed({
                    binding.chatDisplayView.forceScrollToBottom(smooth = true)
                }, 300)  // 增加到300ms，确保流式回复已经开始
            }
        })
    }
    
    private lateinit var displayViewModel: com.example.feishuqa.app.keyboard.ChatDisplayViewModel

    /**
     * 当Activity恢复时刷新登录状态
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshLoginState()
    }

    /**
     * 当Activity销毁时，如果未登录，清理所有 guest 用户的临时对话数据
     */
    override fun onDestroy() {
        super.onDestroy()
        // 如果未登录，删除所有 guest 用户的对话
        if (!SessionManager.isLoggedIn(this)) {
            lifecycleScope.launch {
                viewModel.deleteGuestConversations()
            }
        }
    }
}