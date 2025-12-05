package com.example.feishuqa.app.register

import android.content.Context
import com.example.feishuqa.common.utils.JsonUtils
import com.example.feishuqa.common.utils.JsonUtils.overwriteJsonArray
import com.example.feishuqa.common.utils.JsonUtils.overwriteJsonObject
import com.example.feishuqa.data.entity.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Model层：负责注册账户并写入users.json文件
 *
 */
class RegisterModel(private val context: Context)
{
    private val fileName = "users.json"

    /**
     * 注册
     * @param account 账号
     * @param password 密码
     * @return 注册结果 Result<User>
     */
    suspend fun register(account: String, password: String): Result<User>
    {
        return withContext(Dispatchers.IO)
        {
            try
            {
                // 检查账号是否已存在
                val content = JsonUtils.readJsonFromFiles(context, fileName)
                val jsonArray = if (content.isBlank() || content == "[]")
                {
                    JSONArray()
                }
                else
                {
                    JSONArray(content)
                }

                // 检查账号是否已存在
                for (i in 0 until jsonArray.length())
                {
                    val userJson = jsonArray.getJSONObject(i)
                    if (userJson.getString("name") == account)
                    {
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

                // 保存注册账号信息到users.json文件
                overwriteJsonArray(context, fileName, jsonArray)

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