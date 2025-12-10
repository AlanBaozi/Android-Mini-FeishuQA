package com.example.feishuqa.app.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.feishuqa.R
import com.example.feishuqa.data.entity.Conversation
import com.example.feishuqa.databinding.ItemHistoryConversationBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史对话列表适配器
 */
class HistoryConversationAdapter(
    private val onItemClick: (Conversation) -> Unit,
    private val onDeleteClick: (String) -> Unit,
    private val onRenameClick: (String, String) -> Unit,
    private val onPinClick: (String, Boolean) -> Unit
) : RecyclerView.Adapter<HistoryConversationAdapter.ViewHolder>() {

    // 当前对话列表
    private var conversations: List<Conversation> = emptyList()

    // 当前选中的对话ID
    private var selectedId: String? = null

    /**
     * 更新对话列表
     */
    fun submitList(newList: List<Conversation>) {
        conversations = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ItemHistoryConversationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation) {
            // 设置标题
            binding.tvTitle.text = conversation.getDisplayTitle()

            // 设置最后一条消息
            binding.tvLastMessage.text = conversation.lastMessage ?: ""
            binding.tvLastMessage.visibility = if (conversation.lastMessage != null) View.VISIBLE else View.GONE

            // 设置时间
            binding.tvTime.text = formatTime(conversation.lastMessageTime)

            // 设置置顶图标
            binding.ivPinned.visibility = if (conversation.isPinned) View.VISIBLE else View.GONE

            // 设置选中状态背景
            val isSelected = conversation.id == selectedId
            binding.layoutItem.setBackgroundResource(
                if (isSelected) R.drawable.bg_conversation_item_selected
                else android.R.color.transparent
            )

            // 点击事件
            binding.root.setOnClickListener {
                val previousSelected = selectedId
                selectedId = conversation.id

                // 刷新之前选中项和当前选中项
                if (previousSelected != null) {
                    val previousIndex = conversations.indexOfFirst { it.id == previousSelected }
                    if (previousIndex >= 0) {
                        notifyItemChanged(previousIndex)
                    }
                }
                notifyItemChanged(adapterPosition)

                onItemClick(conversation)
            }

            // 更多操作按钮
            binding.btnMore.setOnClickListener { view ->
                showPopupMenu(view, conversation)
            }
        }

        private fun showPopupMenu(view: View, conversation: Conversation) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.menuInflater.inflate(R.menu.menu_conversation_item, popupMenu.menu)

            // 设置置顶菜单项文本
            val pinMenuItem = popupMenu.menu.findItem(R.id.menu_pin)
            pinMenuItem?.title = if (conversation.isPinned) "取消置顶" else "置顶"

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_rename -> {
                        onRenameClick(conversation.id, conversation.title)
                        true
                    }
                    R.id.menu_pin -> {
                        onPinClick(conversation.id, !conversation.isPinned)
                        true
                    }
                    R.id.menu_delete -> {
                        onDeleteClick(conversation.id)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(conversations[position])
    }

    override fun getItemCount(): Int = conversations.size

    /**
     * 设置选中的对话
     */
    fun setSelectedConversation(id: String?) {
        val previousSelected = selectedId
        selectedId = id

        if (previousSelected != null) {
            val previousIndex = conversations.indexOfFirst { it.id == previousSelected }
            if (previousIndex >= 0) {
                notifyItemChanged(previousIndex)
            }
        }

        if (id != null) {
            val newIndex = conversations.indexOfFirst { it.id == id }
            if (newIndex >= 0) {
                notifyItemChanged(newIndex)
            }
        }
    }
}

