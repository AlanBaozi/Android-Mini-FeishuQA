package com.example.feishuqa.app.history

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.feishuqa.R
import com.example.feishuqa.data.entity.Conversation
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史对话列表 Fragment
 */
class HistoryFragment : Fragment() {

    private lateinit var viewModel: HistoryViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConversationAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var llEmptyState: LinearLayout
    private lateinit var etSearch: EditText

    private var onConversationClick: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 ViewModel
        viewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        // 初始化视图
        initViews(view)

        // 设置 RecyclerView
        setupRecyclerView()

        // 观察数据变化
        observeViewModel()

        // 设置搜索框
        setupSearchBar()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.rvConversations)
        progressBar = view.findViewById(R.id.progressBar)
        tvError = view.findViewById(R.id.tvError)
        llEmptyState = view.findViewById(R.id.llEmptyState)
        etSearch = view.findViewById(R.id.etSearch)
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter { conversationId ->
            onConversationClick?.invoke(conversationId)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { uiState ->
            // 更新加载状态
            progressBar.visibility = if (uiState.isLoading) View.VISIBLE else View.GONE

            // 更新错误状态
            if (uiState.error != null) {
                tvError.visibility = View.VISIBLE
                tvError.text = uiState.error
                recyclerView.visibility = View.GONE
                llEmptyState.visibility = View.GONE
            } else {
                tvError.visibility = View.GONE
            }

            // 更新列表
            val filteredConversations = uiState.getFilteredConversations()
            if (filteredConversations.isEmpty() && !uiState.isLoading) {
                llEmptyState.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                llEmptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.submitList(filteredConversations)
            }
        }
    }

    private fun setupSearchBar() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSearchQuery(s?.toString() ?: "")
            }
        })
    }

    fun setOnConversationClickListener(listener: (String) -> Unit) {
        onConversationClick = listener
    }
}

/**
 * RecyclerView Adapter
 */
class ConversationAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    private var conversations: List<Conversation> = emptyList()

    fun submitList(newList: List<Conversation>) {
        conversations = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.bind(conversation)
        holder.itemView.setOnClickListener {
            onItemClick(conversation.id)
        }
    }

    override fun getItemCount(): Int = conversations.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(conversation: Conversation) {
            tvTitle.text = conversation.getDisplayTitle()
            tvLastMessage.text = conversation.lastMessage ?: ""
            tvTime.text = formatTime(conversation.lastMessageTime)
        }

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
    }
}

