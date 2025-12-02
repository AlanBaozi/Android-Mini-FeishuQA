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
            if (!file.exists()) return "[]"
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
     * @param jsonObject 要写入的 JSON 对象（会直接覆盖原内容）
     *
     * @return Boolean
     *         true：写入成功
     *         false：写入失败（发生异常）
     *
     * 功能说明：
     * - 不读取旧文件内容，直接覆盖写入新的 JSONObject
     * - 如果文件不存在会自动创建
     * - 文件最终内容 = jsonObject.toString()
     */
    fun overwriteJsonObject(context: Context, fileName: String, jsonObject: JSONObject): Boolean
    {
        return try
        {
            val file = File(context.filesDir, fileName)
            file.writeText(jsonObject.toString())   // 直接覆盖写入
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
     * @return Boolean
     *         true：写入成功
     *         false：写入失败（如解析失败或文件写入异常）
     *
     * 功能说明：
     * - 如果文件不存在或为空，则创建一个新的 JSON 数组
     * - 如果文件已存在且是 JSON 数组，则在数组末尾追加新对象
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
     * 获取所有对话文件列表（遍历目录）
     *
     * @param context 上下文，用于获取内部存储路径
     * @return 对话文件名列表，按文件名倒序排列（最新的在前）
     */
    fun getAllConversationFiles(context: Context): List<String>
    {
        return try
        {
            val filesDir = context.filesDir
            val files = filesDir.listFiles()
            files?.filter {
                it.name.startsWith("conversation_") && it.name.endsWith(".json")
            }?.map { it.name }
                ?.sortedDescending()  // 按文件名倒序（时间戳大的在前）
                ?: emptyList()
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 从文件名提取对话 ID（时间戳）
     *
     * @param fileName 文件名，例如 "conversation_1703123456789.json"
     * @return 对话 ID（时间戳字符串），例如 "1703123456789"
     */
    fun extractIdFromFileName(fileName: String): String
    {
        return fileName.removePrefix("conversation_").removeSuffix(".json")
    }

    /**
     * 删除对话文件
     *
     * @param context 上下文
     * @param fileName 文件名
     * @return 是否删除成功
     */
    fun deleteConversationFile(context: Context, fileName: String): Boolean
    {
        return try
        {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
            } else {
                true  // 文件不存在也算成功
            }
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            false
        }
    }
}