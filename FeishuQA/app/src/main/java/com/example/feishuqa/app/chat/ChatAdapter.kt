package com.example.feishuqa.app.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.feishuqa.R
import com.example.feishuqa.data.entity.Message
import com.example.feishuqa.data.entity.MessageType
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.text.SimpleDateFormat
import java.util.*
import kotlin.text.isNotEmpty

class ChatAdapter(private val context: Context, private val onImageClick: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<Message>()
    private val markwon: Markwon
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val tableCache = mutableMapOf<String, Boolean>() // 缓存消息是否包含表格

    companion object {
        const val TYPE_USER = 1
        const val TYPE_AI = 2
    }

    init {
        // 初始化Markwon，支持基础Markdown功能和表格
        markwon = Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }

    fun submitList(list: List<Message>) {
        val oldSize = messages.size
        val newSize = list.size
        
        messages.clear()
        messages.addAll(list)
        
        // 清理表格缓存，避免旧数据影响
        if (oldSize != newSize) {
            tableCache.clear()
        }
        
        // 使用更智能的更新策略
        when {
            // 如果是新消息（列表增长），只通知新增项
            newSize > oldSize -> {
                notifyItemRangeInserted(oldSize, newSize - oldSize)
            }
            // 如果是内容更新（相同大小），只更新变化的项
            newSize == oldSize -> {
                // 对于内容更新，我们暂时使用全量更新，但可以优化为只更新变化的项
                notifyDataSetChanged()
            }
            // 其他情况使用全量更新
            else -> {
                notifyDataSetChanged()
            }
        }
    }
    
    // 新增：更新单个消息的内容（用于流式回复）
    fun updateMessageContent(position: Int, newContent: String) {
        if (position in 0 until messages.size) {
            val oldContent = messages[position].content
            // 只在内容真正变化时更新，避免表格解析时的频繁重绘
            if (oldContent != newContent) {
                messages[position] = messages[position].copy(content = newContent)
                
                // 对于包含表格的内容，使用延迟更新策略减少抖动
                if (newContent.contains("|") && newContent.contains("\n")) {
                    // 延迟100ms更新，让表格解析完成后再刷新
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (position in 0 until messages.size) {
                            notifyItemChanged(position)
                        }
                    }, 100)
                } else {
                    notifyItemChanged(position)
                }
            }
        }
    }
    
    // 新增：获取消息位置
    fun findMessagePosition(messageId: String): Int {
        return messages.indexOfFirst { it.id == messageId }
    }
    
    // 新增：检测消息是否包含表格
    fun isMessageContainsTable(position: Int): Boolean {
        if (position in 0 until messages.size) {
            val message = messages[position]
            // 使用消息ID作为缓存key
            return tableCache.getOrPut(message.id) {
                val content = message.content
                content.contains("|") && content.contains("\n") && 
                content.lines().any { line -> line.trim().startsWith("|") && line.trim().endsWith("|") }
            }
        }
        return false
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser()) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutId = if (viewType == TYPE_USER) R.layout.item_msg_user else R.layout.item_msg_ai
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ChatViewHolder).bind(messages[position])
    }

    override fun getItemCount() = messages.size

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val ivImage: ImageView = itemView.findViewById(R.id.ivMessageImage)
        private val tvTime: TextView? = itemView.findViewById(R.id.tvMessageTime)
        private var lastContent: String? = null // 缓存最后显示的内容

        fun bind(msg: Message) {
            // 设置文字 - 使用Markwon解析Markdown
            if (msg.content.isNotEmpty()) {
                tvContent.visibility = View.VISIBLE
                
                // 只在内容真正变化时重新解析Markdown，避免表格重复解析导致的抖动
                if (lastContent != msg.content) {
                    lastContent = msg.content
                    
                    // 对于包含表格的内容，先设置临时文本，再异步解析
                    if (msg.content.contains("|") && msg.content.contains("\n")) {
                        // 先显示纯文本，避免表格解析过程中的高度变化
                        tvContent.text = msg.content
                        // 延迟解析表格，确保布局稳定
                        tvContent.postDelayed({
                            if (lastContent == msg.content) { // 确保内容没有再次变化
                                markwon.setMarkdown(tvContent, msg.content)
                            }
                        }, 50)
                    } else {
                        markwon.setMarkdown(tvContent, msg.content)
                    }
                }
            } else {
                tvContent.visibility = View.GONE
                lastContent = null
            }

            // 设置图片
            if (msg.type == MessageType.IMAGE && msg.extraInfo != null) {
                ivImage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(msg.extraInfo)
                    .override(800, 800) // 增大显示尺寸
                    .fitCenter()
                    .into(ivImage)

                // 点击图片回调
                ivImage.setOnClickListener { onImageClick(msg.extraInfo) }
            } else {
                ivImage.visibility = View.GONE
            }

            // 设置时间戳
            tvTime?.let {
                it.visibility = View.VISIBLE
                it.text = timeFormat.format(Date(msg.timestamp))
            }
        }
    }
}