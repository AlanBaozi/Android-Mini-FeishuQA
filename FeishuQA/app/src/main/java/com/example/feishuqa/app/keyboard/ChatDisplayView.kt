package com.example.feishuqa.app.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.feishuqa.R
import com.example.feishuqa.app.keyboard.ChatAdapter

class ChatDisplayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var rvChat: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var viewModel: ChatDisplayViewModel
    var onImageClick: ((Any) -> Unit)? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_chat_display, this, true)
        rvChat = view.findViewById(R.id.rvChat)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter { model -> onImageClick?.invoke(model) }
        val layoutManager = LinearLayoutManager(context)
        layoutManager.stackFromEnd = true
        rvChat.layoutManager = layoutManager
        rvChat.adapter = adapter
    }

    fun init(owner: ViewModelStoreOwner, factory: ViewModelProvider.Factory) {
        if (owner !is LifecycleOwner) throw IllegalArgumentException("Owner must be LifecycleOwner")

        viewModel = ViewModelProvider(owner, factory)[ChatDisplayViewModel::class.java]

        viewModel.messages.observe(owner) { list ->
            adapter.submitList(ArrayList(list)) // 提交副本
            if (list.isNotEmpty()) rvChat.post { rvChat.smoothScrollToPosition(list.size - 1) }
        }
    }
}