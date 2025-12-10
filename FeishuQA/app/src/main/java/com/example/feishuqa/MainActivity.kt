package com.example.feishuqa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.feishuqa.app.chat.ChatDisplayViewModel
import com.example.feishuqa.app.keyboard.ChatInputView
import com.example.feishuqa.app.chat.ChatViewModelFactory
import com.example.feishuqa.app.login.LoginActivity
import com.example.feishuqa.app.main.MainView
import com.example.feishuqa.app.main.MainViewModel
import com.example.feishuqa.app.main.MainViewModelFactory
import com.example.feishuqa.common.utils.SessionManager
import com.example.feishuqa.data.entity.AIModels
import com.example.feishuqa.data.repository.ChatRepositoryExample
import com.example.feishuqa.data.repository.MainRepository
import com.example.feishuqa.databinding.ActivityMainBinding
import com.example.feishuqa.databinding.LayoutDrawerBinding
import kotlinx.coroutines.launch

/**
 * 主界面 Activity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var mainView: MainView
    private lateinit var displayViewModel: ChatDisplayViewModel

    // 初始化 Repository 和 Factory
    private val chatViewModelFactory by lazy {
        ChatViewModelFactory(ChatRepositoryExample.getInstance(applicationContext))
    }

    // 权限请求
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show()
    }

    // 相册选择
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) binding.chatInputView.onImageSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 MainViewModel
        viewModel = ViewModelProvider(this, MainViewModelFactory(this))[MainViewModel::class.java]

        // 配置 ChatRepository
        setupChatRepository()

        // 配置 ChatInputView
        setupChatInputView()

        // 初始化 MainView (UI 逻辑)
        val drawerBinding = LayoutDrawerBinding.bind(binding.navDrawer.root)
        mainView = MainView(this, binding, drawerBinding, binding.chatInputView, viewModel, this)
        mainView.init()

        // 初始化聊天显示区域
        initChatDisplay()

        // 监听导航逻辑
        observeNavigation()
    }

    private fun setupChatRepository() {
        val chatRepository = ChatRepositoryExample.getInstance(applicationContext)
        val userId = if (SessionManager.isLoggedIn(this)) {
            SessionManager.getUserId(this) ?: MainRepository.GUEST_USER_ID
        } else {
            MainRepository.GUEST_USER_ID
        }
        chatRepository.setCurrentUserId(userId)
        chatRepository.setCurrentModel(AIModels.defaultModel)

        chatRepository.setOnConversationRefreshListener(object : ChatRepositoryExample.OnConversationRefreshListener {
            override fun onConversationListRefreshNeeded() {
                viewModel.loadConversations()
            }
        })
    }

    private fun setupChatInputView() {
        binding.chatInputView.init(this, chatViewModelFactory)
        binding.chatInputView.setActionListener(object : ChatInputView.ActionListener {
            override fun requestRecordAudioPermission(): Boolean {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return false
            }

            override fun openImagePicker() = pickImageLauncher.launch("image/*")
            override fun onWebSearchClick() = viewModel.toggleWebSearch()
            override fun onModelSelectClick() = mainView.showModelSelectorDialog()

            override fun checkLoginBeforeSend(): Boolean {
                if (!SessionManager.isLoggedIn(this@MainActivity)) {
                    showLoginRequiredDialog()
                    return false
                }
                return true
            }
        })
    }

    private fun initChatDisplay() {
        binding.chatDisplayView.init(this, chatViewModelFactory)

        displayViewModel = ViewModelProvider(this, chatViewModelFactory)[ChatDisplayViewModel::class.java]

        displayViewModel.messages.observe(this) { messages ->
            if (messages.isNullOrEmpty()) {
                binding.welcomeSection.visibility = View.VISIBLE
                binding.chatDisplayView.visibility = View.GONE
            } else {
                binding.welcomeSection.visibility = View.GONE
                binding.chatDisplayView.visibility = View.VISIBLE
            }
        }

        // 监听消息发送与滚动逻辑
        val chatRepository = ChatRepositoryExample.getInstance(applicationContext)
        chatRepository.setOnMessageSendListener(object : ChatRepositoryExample.OnMessageSendListener {
            override fun onMessageSend() {
                binding.chatDisplayView.scrollToBottom(smooth = true)
            }
            override fun onAiMessageAdded() {
                binding.chatDisplayView.postDelayed({
                    binding.chatDisplayView.forceScrollToBottom(smooth = true)
                }, 300)
            }
        })
    }

    private fun observeNavigation() {
        lifecycleScope.launch {
            viewModel.navigateToConversation.collect { conversationId ->
                conversationId?.let {
                    displayViewModel.setCurrentConversation(it)
                    viewModel.clearNavigation()
                }
            }
        }
    }

    private fun showLoginRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage("请先登录")
            .setPositiveButton("去登录") { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLoginState()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!SessionManager.isLoggedIn(this)) {
            lifecycleScope.launch {
                viewModel.deleteGuestConversations()
                mainView.deleteGuestConversations()
            }
        }
    }
}