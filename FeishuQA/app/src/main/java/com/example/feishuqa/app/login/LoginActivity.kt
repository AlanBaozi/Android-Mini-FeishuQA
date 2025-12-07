package com.example.feishuqa.app.login

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.feishuqa.app.login.LoginViewModelFactory
import com.example.feishuqa.databinding.ActivityLoginBinding

/**
 * 登录界面Activity
 * Activity层：只负责初始化和绑定View、ViewModel
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel
    private lateinit var loginView: LoginView
    private var isFirstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 创建ViewModel
        viewModel = ViewModelProvider(this, LoginViewModelFactory(this))[LoginViewModel::class.java]

        // 创建View并初始化
        loginView = LoginView(this, binding, viewModel, this)
        loginView.init()
    }

    override fun onResume() {
        super.onResume()
        // 从其他界面返回时清空输入（首次进入不清空）
        if (!isFirstResume) {
            loginView.clearInputs()
        }
        isFirstResume = false
    }
}
