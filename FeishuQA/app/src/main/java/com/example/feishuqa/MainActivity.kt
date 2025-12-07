package com.example.feishuqa

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import com.example.feishuqa.app.login.LoginActivity
import com.example.feishuqa.app.main.MainView
import com.example.feishuqa.app.main.MainViewModel
import com.example.feishuqa.app.main.MainViewModelFactory
import com.example.feishuqa.common.utils.SessionManager
import com.example.feishuqa.databinding.ActivityMainBinding

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
    private val chatViewModelFactory by lazy {
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
     * 当Activity恢复时刷新登录状态
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshLoginState()
    }
}
