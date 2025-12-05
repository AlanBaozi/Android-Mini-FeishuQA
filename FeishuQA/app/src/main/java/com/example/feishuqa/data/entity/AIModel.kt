package com.example.feishuqa.data.entity

/**
 * AI 模型实体类
 */
data class AIModel(
    val id: String,
    val name: String,
    val description: String = "",
    val isSelected: Boolean = false
)

/**
 * 预定义的AI模型列表
 */
object AIModels {
    val defaultModels = listOf(
        AIModel(
            id = "deepseek-r1",
            name = "DeepSeek R1",
            description = "深度思考，推理能力强"
        ),
        AIModel(
            id = "gpt-4",
            name = "GPT-4",
            description = "OpenAI 最强大模型"
        ),
        AIModel(
            id = "claude-3.5",
            name = "Claude 3.5",
            description = "Anthropic 智能助手"
        ),
        AIModel(
            id = "qwen",
            name = "通义千问",
            description = "阿里巴巴大语言模型"
        ),
        AIModel(
            id = "doubao",
            name = "豆包",
            description = "字节跳动智能助手"
        )
    )
    
    val defaultModel = defaultModels.first()
}








