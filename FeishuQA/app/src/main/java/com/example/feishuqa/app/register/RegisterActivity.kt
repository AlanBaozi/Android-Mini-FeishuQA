package com.example.feishuqa.app.register

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.feishuqa.app.register.RegisterViewModelFactory
import com.example.feishuqa.databinding.ActivityRegisterBinding

/**
 * 注册界面Activity
 * Activity层：只负责初始化和绑定View、ViewModel
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var viewModel: RegisterViewModel
    private lateinit var registerView: RegisterView
    private var isFirstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 创建ViewModel
        viewModel = ViewModelProvider(this, RegisterViewModelFactory(this))[RegisterViewModel::class.java]

        // 创建View并初始化
        registerView = RegisterView(this, binding, viewModel, this)
        registerView.init()
    }

    override fun onResume() {
        super.onResume()
        // 从其他界面返回时清空输入（首次进入不清空）
        if (!isFirstResume) {
            registerView.clearInputs()
        }
        isFirstResume = false
    }
}
