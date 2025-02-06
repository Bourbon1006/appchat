package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.ChatMessage

class SearchResultAdapter(
    private val messages: List<Pair<ChatMessage, Int>>,  // 消息和其在原列表中的位置
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.messageText)
        val timeText: TextView = view.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (message, originalPosition) = messages[position]
        holder.messageText.text = message.content
        holder.timeText.text = message.timestamp.toString()
        holder.itemView.setOnClickListener { onItemClick(originalPosition) }
    }

    override fun getItemCount() = messages.size
} 