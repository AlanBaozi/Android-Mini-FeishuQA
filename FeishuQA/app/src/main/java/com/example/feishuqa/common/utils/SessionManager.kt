package com.example.feishuqa.common.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 登录会话管理器
 * 使用SharedPreferences存储登录状态和用户信息
 */
object SessionManager {

    private const val PREF_NAME = "feishu_qa_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存登录状态
     * @param context 上下文
     * @param userId 用户ID
     * @param userName 用户名
     */
    fun saveLoginSession(context: Context, userId: String, userName: String) {
        getPreferences(context).edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            apply()
        }
    }

    /**
     * 清除登录状态（退出登录）
     * @param context 上下文
     */
    fun clearSession(context: Context) {
        getPreferences(context).edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USER_ID)
            remove(KEY_USER_NAME)
            apply()
        }
    }

    /**
     * 检查是否已登录
     * @param context 上下文
     * @return 是否已登录
     */
    fun isLoggedIn(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * 获取当前登录用户ID
     * @param context 上下文
     * @return 用户ID，未登录返回null
     */
    fun getUserId(context: Context): String? {
        return if (isLoggedIn(context)) {
            getPreferences(context).getString(KEY_USER_ID, null)
        } else {
            null
        }
    }

    /**
     * 获取当前登录用户名
     * @param context 上下文
     * @return 用户名，未登录返回null
     */
    fun getUserName(context: Context): String? {
        return if (isLoggedIn(context)) {
            getPreferences(context).getString(KEY_USER_NAME, null)
        } else {
            null
        }
    }
}


