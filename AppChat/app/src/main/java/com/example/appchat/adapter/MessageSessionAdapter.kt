package com.example.appchat.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.R
import com.example.appchat.model.MessageSession
import com.example.appchat.util.UserPreferences
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.widget.Toast
import kotlinx.coroutines.launch
import com.example.appchat.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.util.Log

class MessageSessionAdapter(
    private val onItemClick: (MessageSession) -> Unit,
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : RecyclerView.Adapter<MessageSessionAdapter.ViewHolder>() {

    private var sessions = mutableListOf<MessageSession>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val name: TextView = view.findViewById(R.id.name)
        val lastMessage: TextView = view.findViewById(R.id.lastMessage)
        val time: TextView = view.findViewById(R.id.time)
        val unreadCount: TextView = view.findViewById(R.id.unreadCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        println("📱 Binding session: partnerId=${session.partnerId}, unreadCount=${session.unreadCount}")
        
        // 构建基础URL
        val baseUrl = holder.itemView.context.getString(
            R.string.server_url_format,
            holder.itemView.context.getString(R.string.server_ip),
            holder.itemView.context.getString(R.string.server_port)
        )
        
        // 根据会话类型构建头像URL
        val avatarUrl = when (session.type?.uppercase()) {
            "GROUP" -> "$baseUrl/api/groups/${session.partnerId}/avatar"
            else -> "$baseUrl/api/users/${session.partnerId}/avatar"
        }
        
        // 加载头像
        Glide.with(holder.itemView.context)
            .load(avatarUrl)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .circleCrop()
            .placeholder(
                if (session.type?.uppercase() == "GROUP") 
                    R.drawable.default_group_avatar 
                else 
                    R.drawable.default_avatar
            )
            .error(
                if (session.type?.uppercase() == "GROUP") 
                    R.drawable.default_group_avatar 
                else 
                    R.drawable.default_avatar
            )
            .into(holder.avatar)

        // 设置名称 - 确保使用 partnerName，它应该已经是 nickname
        holder.name.text = session.partnerName  // partnerName 应该已经是 nickname

        // 设置最后一条消息
        holder.lastMessage.text = session.lastMessage

        // 设置时间
        holder.time.text = formatTime(session.lastMessageTime)

        // 设置未读消息数
        if (session.unreadCount > 0) {
            println("🔴 Showing unread count: ${session.unreadCount}")
            holder.unreadCount.apply {
                visibility = View.VISIBLE
                text = session.unreadCount.toString()
                setBackgroundResource(R.drawable.bg_unread_count)
            }
        } else {
            holder.unreadCount.visibility = View.GONE
        }

        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(session)
        }

        // 添加长按菜单
        holder.itemView.setOnLongClickListener {
            showSessionMenu(holder.itemView.context, session)
            true
        }
    }

    override fun getItemCount() = sessions.size

    fun updateSessions(newSessions: List<MessageSession>) {
        println("🔄 Updating sessions: ${newSessions.size} items")
        sessions.clear()
        // 按最后消息时间排序，最新的在前面
        sessions.addAll(newSessions.sortedByDescending { 
            try {
                LocalDateTime.parse(it.lastMessageTime)
            } catch (e: Exception) {
                // 如果解析失败，使用当前时间作为默认值
                LocalDateTime.now()
            }
        })
        println("📊 Sessions after update: ${sessions.size} items")
        notifyDataSetChanged()
    }

    private fun formatTime(timeString: String): String {
        try {
            // 将字符串解析为 LocalDateTime
            val time = LocalDateTime.parse(timeString)
            val now = LocalDateTime.now()
            
            return when {
                time.toLocalDate() == now.toLocalDate() -> {
                    // 今天，显示时间
                    DateTimeFormatter.ofPattern("HH:mm").format(time)
                }
                time.toLocalDate() == now.toLocalDate().minusDays(1) -> {
                    // 昨天
                    "昨天"
                }
                time.year == now.year -> {
                    // 今年，显示月日
                    DateTimeFormatter.ofPattern("MM-dd").format(time)
                }
                else -> {
                    // 其他年份，显示年月日
                    DateTimeFormatter.ofPattern("yyyy-MM-dd").format(time)
                }
            }
        } catch (e: Exception) {
            // 如果解析失败，直接返回原始字符串
            Log.e("MessageSessionAdapter", "Error parsing time: $timeString", e)
            return timeString
        }
    }

    private fun showSessionMenu(context: Context, session: MessageSession) {
        val options = arrayOf("标记为已读", "删除会话")
        AlertDialog.Builder(context)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> markAsRead(session)
                    1 -> deleteSession(session)
                }
            }
            .show()
    }

    private fun markAsRead(session: MessageSession) {
        val userId = UserPreferences.getUserId(context)
        coroutineScope.launch {
            try {
                println("📱 Marking session as read: userId=$userId, partnerId=${session.partnerId}, type=${session.type ?: "PRIVATE"}")
                val response = ApiClient.apiService.markSessionAsRead(
                    userId = userId,
                    partnerId = session.partnerId,
                    type = session.type ?: "PRIVATE"
                )
                println("📱 markSessionAsRead API response: $response")
                
                if (response.isSuccessful) {
                    // 直接刷新会话列表，而不是修改现有对象
                    val sessions = ApiClient.apiService.getMessageSessions(userId)
                    updateSessions(sessions)
                    Toast.makeText(context, "已标记为已读", Toast.LENGTH_SHORT).show()
                } else {
                    println("❌ Failed to mark session as read: ${response.code()} - ${response.message()}")
                    Toast.makeText(context, "操作失败: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Exception marking session as read: ${e.message}")
                Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteSession(session: MessageSession) {
        AlertDialog.Builder(context)
            .setTitle("删除会话")
            .setMessage("确定要删除与 ${session.partnerName} 的会话吗？")
            .setPositiveButton("确定") { _, _ ->
                val userId = UserPreferences.getUserId(context)
                coroutineScope.launch {
                    try {
                        val response = ApiClient.apiService.deleteSession(
                            userId = userId,
                            partnerId = session.partnerId,
                            type = session.type ?: "PRIVATE"
                        )
                        if (response.isSuccessful) {
                            // 从列表中移除会话
                            val index = sessions.indexOf(session)
                            if (index != -1) {
                                sessions.removeAt(index)
                                notifyItemRemoved(index)
                            }
                            Toast.makeText(context, "会话已删除", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun isGroupSession(session: MessageSession): Boolean {
        return session.type?.equals("GROUP") ?: false
    }
} 