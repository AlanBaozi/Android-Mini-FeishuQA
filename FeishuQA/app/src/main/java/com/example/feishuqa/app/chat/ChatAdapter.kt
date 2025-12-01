package com.example.feishuqa.app.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.feishuqa.R
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.example.feishuqa.data.entity.Message
import com.example.feishuqa.data.entity.MessageStatus

/**
 * 聊天消息列表适配器
 * 支持用户消息和AI消息的差异化显示
 */
class ChatAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2
        private const val TAG = "ChatAdapter"
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isUser()) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            TYPE_AI -> AiViewHolder(inflater.inflate(R.layout.item_message_ai, parent, false))
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserViewHolder -> holder.bind(message)
            is AiViewHolder -> holder.bind(message)
        }
    }

    /**
     * 用户消息ViewHolder
     */
    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val markdownContainer: LinearLayout = itemView.findViewById(R.id.markdownContainer)
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        private val ivImageContent: ImageView = itemView.findViewById(R.id.ivImageContent)

        fun bind(message: Message) {
            try {
                // 根据消息类型显示不同内容
                when (message.type) {
                    // 文本消息使用Markdown渲染
                    else -> {
                        markdownContainer.visibility = View.VISIBLE
                        ivImageContent.visibility = View.GONE
                        MarkdownViewBinder.bind(itemView.context, markdownContainer, message.content)
                    }
                }
            } catch (e: Exception) {
                // 错误处理：显示原始文本
                markdownContainer.visibility = View.VISIBLE
                ivImageContent.visibility = View.GONE
                val errorTextView = TextView(itemView.context).apply {
                    text = message.content
                    setTextColor(itemView.context.getColor(android.R.color.black))
                }
                markdownContainer.removeAllViews()
                markdownContainer.addView(errorTextView)
            }
        }
    }

    /**
     * AI消息ViewHolder
     */
    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val markdownContainer: LinearLayout = itemView.findViewById(R.id.markdownContainer)
        private val cardThinking: View = itemView.findViewById(R.id.cardThinking)
        private val tvThinkingStatus: TextView = itemView.findViewById(R.id.tvThinkingStatus)
        private val layoutActions: View = itemView.findViewById(R.id.layoutActions)
        private val btnCopy: ImageView = itemView.findViewById(R.id.btnCopy)

        fun bind(message: Message) {
            try {
                // 渲染Markdown内容
                MarkdownViewBinder.bind(itemView.context, markdownContainer, message.content)

                // 根据消息长度和状态显示思考卡片
                val shouldShowThinking = message.content.length > 50
                cardThinking.visibility = if (shouldShowThinking) View.VISIBLE else View.GONE

                if (shouldShowThinking) {
                    val statusText = when (message.status) {
                        MessageStatus.TYPING -> "深度思考中..."
                        else -> "已完成深度思考"
                    }
                    tvThinkingStatus.text = statusText
                }

                // 根据状态显示操作栏
                layoutActions.visibility = if (message.status == MessageStatus.TYPING) View.GONE else View.VISIBLE

                // 设置复制按钮点击事件
                btnCopy.setOnClickListener {
                    // 复制消息内容到剪贴板
                    val clipboard = ContextCompat.getSystemService(
                        itemView.context,
                        android.content.ClipboardManager::class.java
                    )
                    val clip = android.content.ClipData.newPlainText("AI消息", message.content)
                    clipboard?.setPrimaryClip(clip)
                    
                    // 显示复制成功提示
                    android.widget.Toast.makeText(
                        itemView.context,
                        "已复制到剪贴板",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                // 错误处理：显示原始文本
                layoutActions.visibility = View.VISIBLE
                cardThinking.visibility = View.GONE
                val errorTextView = TextView(itemView.context).apply {
                    text = message.content
                    setTextColor(itemView.context.getColor(android.R.color.black))
                }
                markdownContainer.removeAllViews()
                markdownContainer.addView(errorTextView)
            }
        }
    }
}

/**
 * 消息差异回调
 */
class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = 
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = 
        oldItem.content == newItem.content && 
        oldItem.status == newItem.status &&
        oldItem.timestamp == newItem.timestamp
}