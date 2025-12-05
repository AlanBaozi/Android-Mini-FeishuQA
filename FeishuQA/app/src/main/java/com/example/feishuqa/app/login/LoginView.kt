package com.example.feishuqa.app.login

import android.content.Context
import android.content.Intent
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.feishuqa.R
import com.example.feishuqa.app.register.RegisterActivity
import com.example.feishuqa.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

/**
 * 登录界面View层
 * View层：负责UI展示和用户交互
 */
class LoginView(
    private val context: Context,
    private val binding: ActivityLoginBinding,
    private val viewModel: LoginViewModel,
    private val lifecycleOwner: LifecycleOwner
) {

    /**
     * 初始化View
     */
    fun init() {
        setupListeners()
        observeViewModel()
        startLogoAnimation()
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            (context as? android.app.Activity)?.finish()
        }

        // 密码显示/隐藏切换
        binding.btnTogglePassword.setOnClickListener {
            viewModel.togglePasswordVisibility()
        }

        // 登录按钮
        binding.btnLogin.setOnClickListener {
            viewModel.login()
        }

        // 跳转注册
        binding.tvRegister.setOnClickListener {
            val intent = Intent(context, RegisterActivity::class.java)
            context.startActivity(intent)
        }

        // 用户名输入框
        binding.etUsername.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.updateUsername(s?.toString() ?: "")
            }
        })

        // 密码输入框
        binding.etPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.updatePassword(s?.toString() ?: "")
            }
        })
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

                // 观察登录成功事件
                viewModel.loginSuccess.collect { user ->
                    user?.let {
                        Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                        viewModel.clearLoginSuccess()
                        (context as? android.app.Activity)?.finish()
                    }
                }
            }
        }
    }

    /**
     * 更新UI
     */
    private fun updateUI(state: LoginUiState) {
        // 更新密码可见性
        if (state.isPasswordVisible) {
            binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
            binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility)
        } else {
            binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_off)
        }
        binding.etPassword.setSelection(binding.etPassword.text.length)

        // 更新加载状态
        binding.btnLogin.isEnabled = !state.isLoading
        if (state.isLoading) {
            binding.btnLogin.text = "登录中..."
        } else {
            binding.btnLogin.text = "登录"
        }

        // 显示错误
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
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
        binding.ivLogo.startAnimation(rotateAnimation)
    }
}




