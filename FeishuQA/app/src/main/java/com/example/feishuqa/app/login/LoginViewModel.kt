package com.example.feishuqa.app.login

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feishuqa.data.entity.User
import com.example.feishuqa.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录界面ViewModel
 * ViewModel层：处理登录业务逻辑
 */
class LoginViewModel(private val context: Context) : ViewModel()
{

    // LogicModel层
    private val model = LoginModel(context)


    // UI状态
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // 登录成功事件
    private val _loginSuccess = MutableStateFlow<User?>(null)
    val loginSuccess: StateFlow<User?> = _loginSuccess.asStateFlow()

    /**
     * 更新用户名
     */
    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    /**
     * 更新密码
     */
    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    /**
     * 切换密码可见性
     */
    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(isPasswordVisible = !_uiState.value.isPasswordVisible)
    }

    /**
     * 执行登录
     */
    fun login() {
        val currentState = _uiState.value
        if (currentState.username.isBlank()) {
            _uiState.value = currentState.copy(error = "请输入用户名")
            return
        }
        if (currentState.password.isBlank()) {
            _uiState.value = currentState.copy(error = "请输入密码")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            val result = model.login(currentState.username, currentState.password)
            Log.d("testLogin", "result = $result")
            result.onSuccess { user ->
                _uiState.value = currentState.copy(isLoading = false)
                _loginSuccess.value = user
            }.onFailure { exception ->
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = exception.message ?: "登录失败"
                )
            }
        }
    }

    /**
     * 清除登录成功事件
     */
    fun clearLoginSuccess() {
        _loginSuccess.value = null
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * 登录界面UI状态
 */
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
