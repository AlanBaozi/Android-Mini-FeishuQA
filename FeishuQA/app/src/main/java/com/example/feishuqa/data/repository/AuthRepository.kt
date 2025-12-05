package com.example.feishuqa.data.repository

import android.content.Context
import com.example.feishuqa.common.utils.JsonUtils
import com.example.feishuqa.data.entity.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 认证相关数据仓库
 * Model层：负责数据获取和处理
 */
class AuthRepository(private val context: Context) {

    private val fileName = "users.json"

    /**
     * 登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果，成功返回User，失败返回null
     */
    suspend fun login(username: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val content = JsonUtils.readJsonFromFiles(context, fileName)
                if (content.isBlank() || content == "[]") {
                    return@withContext Result.failure(Exception("用户不存在"))
                }

                val jsonArray = org.json.JSONArray(content)
                for (i in 0 until jsonArray.length()) {
                    val userJson = jsonArray.getJSONObject(i)
                    val name = userJson.getString("name")
                    val pwd = userJson.getString("password")
                    
                    if (name == username && pwd == password) {
                        val user = User(
                            userId = userJson.getString("userId"),
                            name = name,
                            password = pwd
                        )
                        return@withContext Result.success(user)
                    }
                }
                Result.failure(Exception("用户名或密码错误"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 注册
     * @param account 账号
     * @param password 密码
     * @return 注册结果
     */
    suspend fun register(account: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                // 检查账号是否已存在
                val content = JsonUtils.readJsonFromFiles(context, fileName)
                val jsonArray = if (content.isBlank() || content == "[]") {
                    org.json.JSONArray()
                } else {
                    org.json.JSONArray(content)
                }

                // 检查账号是否已存在
                for (i in 0 until jsonArray.length()) {
                    val userJson = jsonArray.getJSONObject(i)
                    if (userJson.getString("name") == account) {
                        return@withContext Result.failure(Exception("账号已存在"))
                    }
                }

                // 创建新用户
                val userId = "user_${System.currentTimeMillis()}"
                val newUser = JSONObject().apply {
                    put("userId", userId)
                    put("name", account)
                    put("password", password)
                }

                jsonArray.put(newUser)

                // 保存到文件
                val file = java.io.File(context.filesDir, fileName)
                file.writeText(jsonArray.toString())

                val user = User(
                    userId = userId,
                    name = account,
                    password = password
                )
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}




