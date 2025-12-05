package com.example.feishuqa.common.utils.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.feishuqa.common.utils.theme.FeishuBlue
import com.example.feishuqa.common.utils.theme.TextPrimary
import com.example.feishuqa.common.utils.theme.TextSecondary
import com.example.feishuqa.data.entity.AIModel
import com.example.feishuqa.data.entity.AIModels

/**
 * 模型选择按钮 - 显示当前选中的模型
 */
@Composable
fun ModelSelectorButton(
    selectedModel: AIModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF5F6F7),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedModel.name,
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 模型选择对话框
 */
@Composable
fun ModelSelectorDialog(
    models: List<AIModel> = AIModels.defaultModels,
    selectedModel: AIModel,
    onModelSelected: (AIModel) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "选择模型",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                models.forEach { model ->
                    ModelOptionItem(
                        model = model,
                        isSelected = model.id == selectedModel.id,
                        onClick = {
                            onModelSelected(model)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 单个模型选项
 */
@Composable
private fun ModelOptionItem(
    model: AIModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = TextPrimary
            )
            if (model.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = model.description,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        
        // 选中指示器
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = FeishuBlue,
                unselectedColor = Color(0xFFD9D9D9)
            )
        )
    }
}

/**
 * 可组合的模型选择器（带状态管理）
 */
@Composable
fun ModelSelector(
    selectedModel: AIModel,
    onModelChange: (AIModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    
    ModelSelectorButton(
        selectedModel = selectedModel,
        onClick = { showDialog = true },
        modifier = modifier
    )
    
    if (showDialog) {
        ModelSelectorDialog(
            selectedModel = selectedModel,
            onModelSelected = onModelChange,
            onDismiss = { showDialog = false }
        )
    }
}








