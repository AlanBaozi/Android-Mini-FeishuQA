package com.example.feishuqa.app.main

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
// Add this import
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// ... rest of the file
/**
 * 主屏幕视图 - 已迁移到XML实现
 * 此文件保留为参考，主要功能已在MainActivity中实现
 */
@Composable
fun MainScreenView(
    viewModel: MainScreenViewModel = viewModel()
) {
    // 主界面功能已迁移到MainActivity的XML实现
    // 此函数保留为占位符
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "主界面已迁移到XML实现",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
测试