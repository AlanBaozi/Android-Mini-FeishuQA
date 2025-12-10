package com.example.feishuqa.app.chat

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Handler
import android.os.Looper
import com.example.feishuqa.R

class ChatDisplayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private lateinit var rvChat: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var viewModel: ChatDisplayViewModel
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var pendingScrollPosition: Int = -1
    var onImageClick: ((Any) -> Unit)? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_chat_display, this, true)
        rvChat = view.findViewById(R.id.rvChat)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(context) { model -> onImageClick?.invoke(model) }
        val layoutManager = LinearLayoutManager(context)
        layoutManager.stackFromEnd = true
        rvChat.layoutManager = layoutManager
        rvChat.adapter = adapter
    }

    fun init(owner: ViewModelStoreOwner, factory: ViewModelProvider.Factory) {
        if (owner !is LifecycleOwner) throw IllegalArgumentException("Owner must be LifecycleOwner")

        viewModel = ViewModelProvider(owner, factory)[ChatDisplayViewModel::class.java]

        viewModel.messages.observe(owner) { list ->
            val oldSize = adapter.itemCount
            val newSize = list.size
            
            adapter.submitList(ArrayList(list)) // 提交副本
            
            // 优化滚动逻辑：只在必要时滚动，避免流式更新时的抖动
            if (list.isNotEmpty()) {
                rvChat.post {
                    val layoutManager = rvChat.layoutManager as LinearLayoutManager
                    val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                    val isNearBottom = lastVisiblePosition >= newSize - 2 // 判断是否接近底部
                    
                    when {
                        // 如果是新消息，检查是否是用户发送的消息
                        newSize > oldSize -> {
                            val lastMessage = list.lastOrNull()
                            val isUserMessage = lastMessage?.senderId == "user"
                            
                            if (isUserMessage) {
                                // 用户发送的消息，总是滚动到底部，使用平滑动画
                                scheduleScrollToPosition(newSize - 1, smooth = true, forceImmediate = true)
                            } else if (isNearBottom) {
                                // AI回复，只在用户在底部附近时滚动
                                scheduleScrollToPosition(newSize - 1, smooth = true)
                            }
                        }
                        // 如果是内容更新（流式回复），只在用户已经在底部时滚动
                        newSize == oldSize && lastVisiblePosition == newSize - 1 -> {
                            // 使用不带动画的滚动，避免抖动
                            scheduleScrollToPosition(newSize - 1, smooth = false)
                        }
                        // 其他情况不自动滚动，让用户控制
                    }
                }
            }
        }
    }
    
    // 新增：防抖滚动机制（智能延迟，表格内容延长到150ms）
    private fun scheduleScrollToPosition(position: Int, smooth: Boolean = false, forceImmediate: Boolean = false) {
        // 取消之前的滚动任务
        scrollHandler.removeCallbacksAndMessages(null)
        pendingScrollPosition = position
        
        // 如果是强制立即执行（用户发送消息），直接滚动
        if (forceImmediate) {
            if (smooth) {
                rvChat.smoothScrollToPosition(position)
            } else {
                val layoutManager = rvChat.layoutManager as LinearLayoutManager
                layoutManager.scrollToPosition(position)
            }
            pendingScrollPosition = -1
            return
        }
        
        // 智能延迟：检测是否为表格内容
        val delay = if ((rvChat.adapter as? ChatAdapter)?.isMessageContainsTable(position) == true) {
            150L // 表格内容使用150ms延迟，给表格渲染更多时间
        } else {
            100L // 普通内容使用100ms延迟
        }
        
        // 延迟执行滚动，避免过于频繁的更新
        scrollHandler.postDelayed({
            if (pendingScrollPosition == position) {
                if (smooth) {
                    rvChat.smoothScrollToPosition(position)
                } else {
                    val layoutManager = rvChat.layoutManager as LinearLayoutManager
                    layoutManager.scrollToPosition(position)
                }
                pendingScrollPosition = -1
            }
        }, delay)
    }
    
    // 新增：立即滚动到底部的方法，供外部调用
    fun scrollToBottom(smooth: Boolean = true) {
        val itemCount = adapter.itemCount
        if (itemCount > 0) {
            if (smooth) {
                rvChat.smoothScrollToPosition(itemCount - 1)
            } else {
                val layoutManager = rvChat.layoutManager as LinearLayoutManager
                layoutManager.scrollToPosition(itemCount - 1)
            }
        }
    }
    
    // 新增：强制滚动到底部（忽略内部智能滚动逻辑）
    fun forceScrollToBottom(smooth: Boolean = true) {
        val itemCount = adapter.itemCount
        if (itemCount > 0) {
            // 取消所有待处理的滚动任务
            scrollHandler.removeCallbacksAndMessages(null)
            pendingScrollPosition = -1
            
            if (smooth) {
                rvChat.smoothScrollToPosition(itemCount - 1)
            } else {
                val layoutManager = rvChat.layoutManager as LinearLayoutManager
                layoutManager.scrollToPosition(itemCount - 1)
            }
        }
    }
    
    // 新增：清理资源
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scrollHandler.removeCallbacksAndMessages(null)
    }
}