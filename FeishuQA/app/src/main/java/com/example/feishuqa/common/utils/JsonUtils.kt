package com.example.feishuqa.common.utils

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 读取本地JSON文件的工具类
 */
object JsonUtils
{

    /**
     * 从 assets 中读取 json 文件
     * @param context 上下文
     * @param fileName 文件名（例如 "user.json"）,路径为FeishuQA\app\src\main\assets
     */
    fun readJson(context: Context, fileName: String): String
    {
        return try
        {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()

            var line: String? = reader.readLine()
            while (line != null) {
                stringBuilder.append(line)
                line = reader.readLine()
            }

            reader.close()
            stringBuilder.toString()
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            ""
        }
    }
}