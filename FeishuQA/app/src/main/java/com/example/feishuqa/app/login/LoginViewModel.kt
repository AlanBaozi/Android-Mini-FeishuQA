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
        
        // 收集所有未填写的字段
        val missingFields = mutableListOf<String>()
        var hasUsernameError = false
        var hasPasswordError = false
        
        if (currentState.username.isBlank()) {
            missingFields.add("用户名")
            hasUsernameError = true
        }
        if (currentState.password.isBlank()) {
            missingFields.add("密码")
            hasPasswordError = true
        }
        
        // 如果有未填写的字段，弹窗提示
        if (missingFields.isNotEmpty()) {
            _uiState.value = currentState.copy(
                dialogError = "请输入${missingFields.joinToString("、")}",
                usernameError = hasUsernameError,
                passwordError = hasPasswordError
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null, dialogError = null)
            val result = model.login(currentState.username, currentState.password)
            Log.d("testLogin", "result = $result")
            result.onSuccess { user ->
                _uiState.value = currentState.copy(isLoading = false)
                _loginSuccess.value = user
            }.onFailure { exception ->
                _uiState.value = currentState.copy(
                    isLoading = false,
                    dialogError = exception.message ?: "登录失败"
                )
            }
        }
    }

    /**
     * 检查用户名字段（当密码获得焦点时调用）
     */
    fun validateUsernameOnFocusChange() {
        val currentState = _uiState.value
        if (currentState.username.isBlank()) {
            _uiState.value = currentState.copy(usernameError = true)
        }
    }

    /**
     * 清除用户名错误状态
     */
    fun clearUsernameError() {
        _uiState.value = _uiState.value.copy(usernameError = false)
    }

    /**
     * 清除密码错误状态
     */
    fun clearPasswordError() {
        _uiState.value = _uiState.value.copy(passwordError = false)
    }

    /**
     * 清除弹窗错误
     */
    fun clearDialogError() {
        _uiState.value = _uiState.value.copy(dialogError = null)
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

    /**
     * 清空所有输入（返回界面时调用）
     */
    fun clearAllInputs() {
        _uiState.value = LoginUiState()
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
    val error: String? = null,
    val dialogError: String? = null, // 用于弹窗显示的错误
    val usernameError: Boolean = false, // 用户名字段是否有错误（高亮）
    val passwordError: Boolean = false // 密码字段是否有错误（高亮）
)
