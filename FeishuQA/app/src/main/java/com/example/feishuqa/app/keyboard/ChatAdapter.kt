package com.example.feishuqa.app.keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.feishuqa.R
import com.example.feishuqa.data.entity.ChatMessage
import kotlin.text.isNotEmpty

class ChatAdapter(private val onImageClick: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    companion object {
        const val TYPE_USER = 1
        const val TYPE_AI = 2
    }

    fun submitList(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_AI
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

        fun bind(msg: ChatMessage) {
            // 设置文字
            if (msg.content.isNotEmpty()) {
                tvContent.visibility = View.VISIBLE
                tvContent.text = msg.content
            } else {
                tvContent.visibility = View.GONE
            }

            // 设置图片
            if (msg.localImagePath != null) {
                ivImage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(msg.localImagePath)
                    .override(600, 600) // 限制显示尺寸
                    .centerCrop()
                    .into(ivImage)

                // 点击图片回调
                ivImage.setOnClickListener { onImageClick(msg.localImagePath) }
            } else {
                ivImage.visibility = View.GONE
            }
        }
    }
}