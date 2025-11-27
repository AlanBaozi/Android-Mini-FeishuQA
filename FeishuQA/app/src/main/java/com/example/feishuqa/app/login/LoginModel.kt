package com.example.feishuqa.app.login

import android.content.Context
import com.example.feishuqa.common.utils.JsonUtils
import com.example.feishuqa.data.entity.User
import org.json.JSONArray

/**
 * Model 层：只负责读取和解析 user.json 文件
 * 不负责校验用户名密码
 */
class LoginModel
{

    /**
     * 加载 assets 下的 user.json
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
}
