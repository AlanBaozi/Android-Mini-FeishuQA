package com.example.feishuqa.common.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 读写JSON文件的工具类
 */
object JsonUtils
{

    /**
     * 从 assets 中读取 json 文件
     * @param context 上下文
     * @param fileName 文件名（例如 "user.json"）,路径为FeishuQA\app\src\main\assets
     *
     * @return 文件内容字符串，如果文件不存在或读取失败，则返回 "[]"
     */
    fun readJson(context: Context, fileName: String): String
    {
        return try
        {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()

            var line: String? = reader.readLine()
            while (line != null)
            {
                stringBuilder.append(line)
                line = reader.readLine()
            }

            reader.close()
            stringBuilder.toString()
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            "[]"
        }
    }


    /**
     * 读取内部存储 JSON 文件内容
     *
     * @param context 上下文，用于获取内部存储路径
     * @param fileName 文件名，例如 "conversation_1.json"
     *                 文件存放在内部存储目录：/data/data/com.example.feishuqa/files/
     *                 通过View → Tool Windows → Device File Explorer查看
     * @return 文件内容字符串，如果文件不存在或读取失败，则返回 "[]"
     */
    fun readJsonFromFiles(context: Context, fileName: String): String
    {
        return try
        {
            val file = File(context.filesDir, fileName)
            if (!file.exists())
                return "[]"
            file.readText()
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            "[]"
        }
    }

    /**
     * 覆盖写入 JSON 对象到内部存储文件（不追加，直接覆盖）
     *
     * @param context 上下文，用于获取内部存储路径（context.filesDir）
     * @param fileName 文件名，例如 "conversation.json"
     * @param jsonObject 要写入的JSON对象
     *
     * @return 返回写入操作的结果
     *
     * - 不读取旧文件内容，直接覆盖写入新的 JSONObject
     */
    fun overwriteJsonObject(context: Context, fileName: String, jsonObject: JSONObject): Boolean
    {
        return try
        {
            val file = File(context.filesDir, fileName)
            val parent = file.parentFile
            if (parent != null && !parent.exists())
            {
                parent.mkdirs() // 自动创建目录
            }
            file.writeText(jsonObject.toString())   // 将JSONObject写入
            true
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            false
        }
    }

    /**
     * 覆盖写入JSON数组到内部存储文件（不追加，直接覆盖）
     *
     * @param context 上下文
     * @param fileName 文件名
     * @param JSONArray 要写入的JSON数组对象
     *
     * @return 返回写入操作的结果
     */
    fun overwriteJsonArray(context: Context, fileName: String, jsonArray: JSONArray): Boolean
    {
        return try
        {
            val file = File(context.filesDir, fileName)
            val parent = file.parentFile
            if (parent != null && !parent.exists())
            {
                parent.mkdirs() // 自动创建目录
            }
            file.writeText(jsonArray.toString()) // 将JSONArray写入
            true
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            false
        }
    }


    /**
     * 向内部存储 JSON 文件追加一个 JSONObject 对象
     *
     * @param context 上下文，用于获取内部存储路径
     * @param fileName 文件名，例如 "conversation.json"
     * @param newObject 要追加的新 JSON 对象
     *
     * @return 返回写入操作的结果
     *
     * - 写回文件时会覆盖原文件，但内容已包含原有数据 + 新对象
     */
    fun appendJsonObject(context: Context, fileName: String, newObject: JSONObject): Boolean
    {
        return try
        {
            // 读取原有内容
            val content = readJsonFromFiles(context, fileName)
            val jsonArray = if (content.isBlank()) {
                JSONArray()  // 文件不存在或为空，创建空数组
            } else {
                JSONArray(content)  // 文件存在，解析已有数组
            }

            // 添加新对象
            jsonArray.put(newObject)

            // 写回文件
            val file = File(context.filesDir, fileName)
            val parent = file.parentFile
            if (parent != null && !parent.exists())
            {
                parent.mkdirs()
            }
            file.writeText(jsonArray.toString())
            true
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            false
        }
    }

    /**
     * 删除JSON文件
     *
     * @param context 上下文
     * @param fileName 文件名
     *
     * @return 是否删除成功
     *
     * 可以删除文件，也可以删除空目录。
     */
    fun deleteJSONFile(context: Context, fileName: String): Boolean
    {
        return try
        {
            val file = File(context.filesDir, fileName)
            if (file.exists())
            {
                file.delete()
            }
            else
            {
                true
            }
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            false
        }
    }
}