package com.example.feishuqa.app.login

import android.content.Context
import com.example.feishuqa.common.utils.JsonUtils
import com.example.feishuqa.data.entity.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Model 层：负责读取和解析user.json文件
 *          并负责校验用户名密码
 */
class LoginModel(private val context: Context)
{

    /**
     * 加载 assets 下的 user.json
     * 暂时不用
     */
    fun loadUsers(context: Context): List<User>
    {
        val jsonStr = JsonUtils.readJson(context, "user/user.json")
        if (jsonStr.isEmpty())
            return emptyList()

        val jsonArray = JSONArray(jsonStr)
        val userList = mutableListOf<User>()

        for (i in 0 until jsonArray.length())
        {
            val obj = jsonArray.getJSONObject(i)
            userList.add(
                User(
                    userId = obj.getString("userId"),
                    name = obj.getString("name"),
                    password = obj.getString("password")
                )
            )
        }

        return userList
    }

    private val fileName = "users.json"

    /**
     * 登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果，成功返回Result<User>，失败返回null
     */
    suspend fun login(username: String, password: String): Result<User>
    {
        return withContext(Dispatchers.IO)
        {
            try
            {
                // 读取/data/data/users.json文件
                val content = JsonUtils.readJsonFromFiles(context, fileName)
                if (content.isBlank() || content == "[]")
                {
                    return@withContext Result.failure(Exception("用户不存在"))
                }

                val jsonArray = JSONArray(content)
                for (i in 0 until jsonArray.length())
                {
                    val userJson = jsonArray.getJSONObject(i)
                    val name = userJson.getString("name")
                    if (name == username)
                    {
                        val pwd = userJson.getString("password")
                        // 用户存在，检查密码
                        if (pwd == password)
                        {
                            // 密码正确，返回登录成功
                            val user = User(
                                userId = userJson.getString("userId"),
                                name = name,
                                password = pwd
                            )
                            return@withContext Result.success(user)
                        }
                        else
                        {
                            // 用户名对，但密码错，返回密码错误
                            return@withContext Result.failure(Exception("密码错误"))
                        }
                    }
                }
                Result.failure(Exception("账号不存在"))
            }
            catch (e: Exception)
            {
                Result.failure(e)
            }
        }
    }
}
