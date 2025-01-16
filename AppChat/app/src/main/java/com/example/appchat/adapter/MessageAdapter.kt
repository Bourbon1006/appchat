package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.ChatMessage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MessageAdapter(private val currentUserId: Long) : 
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_MY_MESSAGE) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        
        return MessageViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_MY_MESSAGE
        } else {
            VIEW_TYPE_OTHER_MESSAGE
        }
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView? = itemView.findViewById(R.id.nameText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView? = itemView.findViewById(R.id.timeText)
        
        fun bind(message: ChatMessage) {
            nameText?.text = message.senderName
            messageText.text = message.content
            timeText?.text = message.timestamp?.let { formatTime(it) } ?: ""
        }

        private fun formatTime(timestamp: LocalDateTime): String {
            return try {
                DateTimeFormatter.ofPattern("HH:mm").format(timestamp)
            } catch (e: Exception) {
                ""
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_MY_MESSAGE = 1
        private const val VIEW_TYPE_OTHER_MESSAGE = 2
    }
} 