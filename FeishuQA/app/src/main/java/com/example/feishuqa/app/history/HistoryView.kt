package com.example.feishuqa.app.history

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.feishuqa.R
import com.example.feishuqa.databinding.LayoutDrawerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 历史对话 View 组件
 * 封装历史对话列表的 UI 逻辑，可在主界面的 drawer 中使用
 */
class HistoryView(
    private val context: Context,
    private val drawerBinding: LayoutDrawerBinding,
    private val viewModel: HistoryViewModel,
    private val lifecycleOwner: LifecycleOwner
) {

    private lateinit var historyAdapter: HistoryConversationAdapter

    // 回调接口
    var onConversationClick: ((String) -> Unit)? = null
    var onCloseDrawer: (() -> Unit)? = null

    /**
     * 初始化历史对话 View
     */
    fun init() {
        setupHistoryList()
        observeViewModel()
    }

    /**
     * 设置历史对话列表
     */
    private fun setupHistoryList() {
        historyAdapter = HistoryConversationAdapter(
            onItemClick = { conversation ->
                onCloseDrawer?.invoke()
                onConversationClick?.invoke(conversation.id)
            },
            onDeleteClick = { conversationId ->
                viewModel.deleteConversation(conversationId)
            },
            onRenameClick = { conversationId, currentTitle ->
                showRenameDialog(conversationId, currentTitle)
            },
            onPinClick = { conversationId, isPinned ->
                viewModel.togglePinConversation(conversationId)
            }
        )

        drawerBinding.rvHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }

        // 设置搜索框
        drawerBinding.etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSearchQuery(s?.toString() ?: "")
            }
        })
    }

    /**
     * 显示重命名对话框
     */
    private fun showRenameDialog(conversationId: String, currentTitle: String) {
        val input = android.widget.EditText(context)
        input.setText(currentTitle)
        input.selectAll()

        AlertDialog.Builder(context)
            .setTitle("重命名对话")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    viewModel.renameConversation(conversationId, newTitle)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 观察 ViewModel 状态变化
     */
    private fun observeViewModel() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    /**
     * 更新 UI
     * 注意：此方法在 collect 回调中调用，已经在协程作用域内
     */
    private suspend fun updateUI(state: HistoryUiState) {
        // 对于大量数据的过滤和排序，在后台线程执行
        // 但如果数据量不大（< 50条），在主线程执行也可以接受
        // 这里为了性能考虑，如果数据量较大则在后台线程处理
        val filteredConversations = if (state.conversations.size > 50) {
            // 数据量较大，在后台线程处理
            withContext(Dispatchers.Default) {
                state.getFilteredConversations()
            }
        } else {
            // 数据量较小，直接在主线程处理
            state.getFilteredConversations()
        }
        // 更新 UI（在主线程）
        updateUIInternal(filteredConversations, state)
    }

    /**
     * 内部方法：更新 UI 组件（必须在主线程调用）
     */
    private fun updateUIInternal(filteredConversations: List<com.example.feishuqa.data.entity.Conversation>, state: HistoryUiState) {
        // 更新对话列表（使用过滤后的列表）
        historyAdapter.submitList(filteredConversations)
        historyAdapter.setSelectedConversation(state.selectedConversationId)

        // 更新空状态显示
        if (filteredConversations.isEmpty() && !state.isLoading) {
            drawerBinding.rvHistory.visibility = View.GONE
            drawerBinding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            drawerBinding.rvHistory.visibility = View.VISIBLE
            drawerBinding.layoutEmptyState.visibility = View.GONE
        }

        // 显示错误
        state.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    /**
     * 设置用户ID（当用户登录/登出时调用）
     */
    fun setUserId(userId: String) {
        viewModel.setUserId(userId)
    }

    /**
     * 刷新对话列表
     */
    fun refresh() {
        viewModel.loadConversations()
    }
}
