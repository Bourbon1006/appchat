package com.example.appchat.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SearchResultAdapter(
    private val context: Context,
    private val messages: List<Pair<ChatMessage, Int>>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contentTextView: TextView = view.findViewById(R.id.messageContent)
        val senderTextView: TextView = view.findViewById(R.id.messageSender)
        val timeTextView: TextView = view.findViewById(R.id.messageTime)
        val chatTypeTextView: TextView = view.findViewById(R.id.chatType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (message, originalPosition) = messages[position]
        
        // 设置消息内容
        holder.contentTextView.text = message.content
        
        // 设置发送者信息
        val senderName = message.senderName
        
        // 设置聊天类型和相关信息
        if (message.groupId != null) {
            // 群聊消息
            val groupName = message.groupName ?: "未知群组"
            holder.chatTypeTextView.text = "群聊: $groupName"
            holder.senderTextView.text = "$senderName 在群聊中发送"
        } else {
            // 私聊消息
            holder.chatTypeTextView.text = "私聊"
            holder.senderTextView.text = senderName
        }
        
        // 设置时间
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedTime = message.timestamp?.let { timestamp ->
            try {
                sdf.format(timestamp)
            } catch (e: Exception) {
                ""
            }
        } ?: ""
        holder.timeTextView.text = formattedTime
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(originalPosition)
        }
    }

    override fun getItemCount() = messages.size
}