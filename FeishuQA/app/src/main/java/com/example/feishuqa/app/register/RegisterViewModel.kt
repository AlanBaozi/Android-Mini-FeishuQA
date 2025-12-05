package com.example.feishuqa.app.register

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feishuqa.data.entity.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 注册界面ViewModel
 * ViewModel层：处理注册业务逻辑
 */
class RegisterViewModel(private val context: Context) : ViewModel() {

    // LogicModel层
    private val model = RegisterModel(context)

    // UI状态
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    // 注册成功事件
    private val _registerSuccess = MutableStateFlow<User?>(null)
    val registerSuccess: StateFlow<User?> = _registerSuccess.asStateFlow()

    /**
     * 更新账号
     */
    fun updateAccount(account: String) {
        _uiState.value = _uiState.value.copy(account = account)
    }

    /**
     * 更新密码
     */
    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    /**
     * 更新确认密码
     */
    fun updateConfirmPassword(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword)
    }

    /**
     * 切换密码可见性
     */
    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(isPasswordVisible = !_uiState.value.isPasswordVisible)
    }

    /**
     * 切换确认密码可见性
     */
    fun toggleConfirmPasswordVisibility() {
        _uiState.value = _uiState.value.copy(isConfirmPasswordVisible = !_uiState.value.isConfirmPasswordVisible)
    }

    /**
     * 执行注册
     */
    fun register() {
        val currentState = _uiState.value
        if (currentState.account.isBlank()) {
            _uiState.value = currentState.copy(error = "请输入账号")
            return
        }
        if (currentState.password.isBlank()) {
            _uiState.value = currentState.copy(error = "请输入密码")
            return
        }
        if (currentState.confirmPassword.isBlank()) {
            _uiState.value = currentState.copy(error = "请确认密码")
            return
        }
        if (currentState.password != currentState.confirmPassword) {
            _uiState.value = currentState.copy(error = "两次密码输入不一致")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            val result = model.register(currentState.account, currentState.password)
            Log.d("testRegister", "result = $result")
            result.onSuccess { user ->
                _uiState.value = currentState.copy(isLoading = false)
                _registerSuccess.value = user
            }.onFailure { exception ->
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = exception.message ?: "注册失败"
                )
            }
        }
    }

    /**
     * 清除注册成功事件
     */
    fun clearRegisterSuccess() {
        _registerSuccess.value = null
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * 注册界面UI状态
 */
data class RegisterUiState(
    val account: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)



