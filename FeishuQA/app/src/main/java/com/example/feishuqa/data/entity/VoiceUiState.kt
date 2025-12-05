package com.example.feishuqa.data.entity
import android.graphics.Color

data class VoiceUiState(
    val isDialogVisible: Boolean = false,
    val displayContent: String = "",
    val hintText: String = "松开转文字，上滑取消",
    val hintColor: Int = Color.parseColor("#999999"),
    val contentColor: Int = Color.parseColor("#3370FF"),
    val bubbleAlpha: Float = 1.0f,
    val volume: Float = 0f
)
