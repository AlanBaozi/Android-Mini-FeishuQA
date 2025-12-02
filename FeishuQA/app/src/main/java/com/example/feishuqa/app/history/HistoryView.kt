package com.example.feishuqa.app.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feishuqa.data.entity.Conversation
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史对话列表 Drawer 内容
 */
@Composable
fun HistoryView(
    onConversationClick: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel { HistoryViewModel(LocalContext.current) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredConversations = uiState.getFilteredConversations()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 顶部标题栏
        HistoryHeader()

        // 搜索框
        HistorySearchBar(
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = { viewModel.updateSearchQuery(it) }
        )

        // 加载状态
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            // 错误状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "加载失败",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        } else if (filteredConversations.isEmpty()) {
            // 空状态
            EmptyHistoryContent()
        } else {
            // 对话列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = filteredConversations,
                    key = { it.id }
                ) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        isSelected = conversation.id == uiState.selectedConversationId,
                        onClick = {
                            viewModel.selectConversation(conversation.id)
                            onConversationClick(conversation.id)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 顶部标题栏
 */
@Composable
fun HistoryHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "历史对话",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

/**
 * 搜索框
 */
@Composable
fun HistorySearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("搜索对话...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "搜索")
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "清除",
                        tint = Color.Gray
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF3370FF),
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
        )
    )
}

/**
 * 对话列表项（只展示，无操作按钮）
 */
@Composable
fun ConversationItem(
    conversation: com.example.feishuqa.data.entity.Conversation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        Color(0xFFE8F2FF)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 对话内容
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = conversation.getDisplayTitle(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                conversation.lastMessage?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(conversation.lastMessageTime),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * 空状态内容
 */
@Composable
fun EmptyHistoryContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无历史对话",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}

/**
 * 格式化时间显示
 */
private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "刚刚"
        diff < 3600000 -> "${diff / 60000}分钟前"
        diff < 86400000 -> "${diff / 3600000}小时前"
        diff < 604800000 -> "${diff / 86400000}天前"
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
    }
}

// ==================== Preview 预览函数 ====================

/**
 * 预览：对话列表项
 */
@Preview(showBackground = true, name = "对话列表项")
@Composable
fun PreviewConversationItem() {
    val mockConversation = Conversation(
        id = "1",
        title = "如何学习 Kotlin？",
        lastMessage = "Kotlin 是一种现代化的编程语言，具有简洁的语法...",
        lastMessageTime = System.currentTimeMillis() - 3600000,
        messageCount = 5,
        isPinned = false
    )
    
    ConversationItem(
        conversation = mockConversation,
        isSelected = false,
        onClick = {}
    )
}

/**
 * 预览：选中的对话列表项
 */
@Preview(showBackground = true, name = "选中的对话项")
@Composable
fun PreviewSelectedConversationItem() {
    val mockConversation = Conversation(
        id = "1",
        title = "如何学习 Kotlin？",
        lastMessage = "Kotlin 是一种现代化的编程语言，具有简洁的语法...",
        lastMessageTime = System.currentTimeMillis() - 3600000,
        messageCount = 5,
        isPinned = true
    )
    
    ConversationItem(
        conversation = mockConversation,
        isSelected = true,
        onClick = {}
    )
}

/**
 * 预览：搜索框
 */
@Preview(showBackground = true, name = "搜索框")
@Composable
fun PreviewHistorySearchBar() {
    HistorySearchBar(
        searchQuery = "",
        onSearchQueryChange = {}
    )
}

/**
 * 预览：带内容的搜索框
 */
@Preview(showBackground = true, name = "搜索框（有内容）")
@Composable
fun PreviewHistorySearchBarWithText() {
    HistorySearchBar(
        searchQuery = "Kotlin",
        onSearchQueryChange = {}
    )
}

/**
 * 预览：顶部标题栏
 */
@Preview(showBackground = true, name = "顶部标题栏")
@Composable
fun PreviewHistoryHeader() {
    HistoryHeader()
}

/**
 * 预览：空状态
 */
@Preview(showBackground = true, name = "空状态")
@Composable
fun PreviewEmptyHistoryContent() {
    EmptyHistoryContent()
}

/**
 * 预览：完整的历史对话列表（模拟数据）
 */
@Preview(showBackground = true, name = "历史对话列表", widthDp = 360, heightDp = 640)
@Composable
fun PreviewHistoryView() {
    // 创建模拟数据
    val mockConversations = listOf(
        Conversation(
            id = "1",
            title = "如何学习 Kotlin？",
            lastMessage = "Kotlin 是一种现代化的编程语言，具有简洁的语法...",
            lastMessageTime = System.currentTimeMillis() - 3600000,
            messageCount = 5,
            isPinned = true
        ),
        Conversation(
            id = "2",
            title = "Compose 布局问题",
            lastMessage = "可以使用 Column 和 Row 来实现布局...",
            lastMessageTime = System.currentTimeMillis() - 7200000,
            messageCount = 3,
            isPinned = false
        ),
        Conversation(
            id = "3",
            title = "MVVM 架构模式",
            lastMessage = "MVVM 包含 Model、View、ViewModel 三层...",
            lastMessageTime = System.currentTimeMillis() - 86400000,
            messageCount = 8,
            isPinned = false
        )
    )
    
    // 注意：预览时不能使用真实的 ViewModel，需要模拟 UI 状态
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        HistoryHeader()
        HistorySearchBar(
            searchQuery = "",
            onSearchQueryChange = {}
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = mockConversations,
                key = { it.id }
            ) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    isSelected = conversation.id == "1",
                    onClick = {}
                )
            }
        }
    }
}
