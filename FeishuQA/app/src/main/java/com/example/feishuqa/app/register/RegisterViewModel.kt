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
        
        // 收集所有未填写的字段
        val missingFields = mutableListOf<String>()
        var hasAccountError = false
        var hasPasswordError = false
        var hasConfirmPasswordError = false
        
        if (currentState.account.isBlank()) {
            missingFields.add("账号")
            hasAccountError = true
        }
        if (currentState.password.isBlank()) {
            missingFields.add("密码")
            hasPasswordError = true
        }
        if (currentState.confirmPassword.isBlank()) {
            missingFields.add("确认密码")
            hasConfirmPasswordError = true
        }
        
        // 如果有未填写的字段，弹窗提示
        if (missingFields.isNotEmpty()) {
            _uiState.value = currentState.copy(
                dialogError = "请输入${missingFields.joinToString("、")}",
                accountError = hasAccountError,
                passwordError = hasPasswordError,
                confirmPasswordError = hasConfirmPasswordError
            )
            return
        }
        
        // 检查两次密码是否一致
        if (currentState.password != currentState.confirmPassword) {
            _uiState.value = currentState.copy(
                dialogError = "两次密码输入不一致",
                confirmPasswordError = true
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null, dialogError = null)
            val result = model.register(currentState.account, currentState.password)
            Log.d("testRegister", "result = $result")
            result.onSuccess { user ->
                _uiState.value = currentState.copy(isLoading = false)
                _registerSuccess.value = user
            }.onFailure { exception ->
                _uiState.value = currentState.copy(
                    isLoading = false,
                    dialogError = exception.message ?: "注册失败"
                )
            }
        }
    }

    /**
     * 检查账号字段（当密码获得焦点时调用）
     */
    fun validateAccountOnFocusChange() {
        val currentState = _uiState.value
        if (currentState.account.isBlank()) {
            _uiState.value = currentState.copy(accountError = true)
        }
    }

    /**
     * 检查密码字段（当确认密码获得焦点时调用）
     */
    fun validatePasswordOnFocusChange() {
        val currentState = _uiState.value
        var hasAccountError = false
        var hasPasswordError = false
        
        if (currentState.account.isBlank()) {
            hasAccountError = true
        }
        if (currentState.password.isBlank()) {
            hasPasswordError = true
        }
        
        _uiState.value = currentState.copy(
            accountError = hasAccountError,
            passwordError = hasPasswordError
        )
    }

    /**
     * 清除账号错误状态
     */
    fun clearAccountError() {
        _uiState.value = _uiState.value.copy(accountError = false)
    }

    /**
     * 清除密码错误状态
     */
    fun clearPasswordError() {
        _uiState.value = _uiState.value.copy(passwordError = false)
    }

    /**
     * 清除确认密码错误状态
     */
    fun clearConfirmPasswordError() {
        _uiState.value = _uiState.value.copy(confirmPasswordError = false)
    }

    /**
     * 清除弹窗错误
     */
    fun clearDialogError() {
        _uiState.value = _uiState.value.copy(dialogError = null)
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

    /**
     * 清空所有输入（返回界面时调用）
     */
    fun clearAllInputs() {
        _uiState.value = RegisterUiState()
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
    val error: String? = null,
    val dialogError: String? = null, // 用于弹窗显示的错误
    val accountError: Boolean = false, // 账号字段是否有错误（高亮）
    val passwordError: Boolean = false, // 密码字段是否有错误（高亮）
    val confirmPasswordError: Boolean = false // 确认密码字段是否有错误（高亮）
)



