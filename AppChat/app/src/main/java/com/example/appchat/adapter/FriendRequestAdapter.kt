package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.UserDTO
import com.example.appchat.util.loadAvatar

class FriendRequestAdapter(
    private val onAccept: (UserDTO) -> Unit,
    private val onReject: (UserDTO) -> Unit
) : ListAdapter<UserDTO, FriendRequestAdapter.ViewHolder>(UserDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.requestAvatar)
        val username: TextView = view.findViewById(R.id.requestUsername)
        val acceptButton: Button = view.findViewById(R.id.acceptButton)
        val rejectButton: Button = view.findViewById(R.id.rejectButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        
        // 设置用户名
        holder.username.text = user.username
        
        // 加载头像
        val baseUrl = holder.itemView.context.getString(
            R.string.server_url_format,
            holder.itemView.context.getString(R.string.server_ip),
            holder.itemView.context.getString(R.string.server_port)
        )
        val avatarUrl = "$baseUrl/api/users/${user.id}/avatar"
        
        // 使用扩展函数加载头像
        holder.avatar.loadAvatar(avatarUrl)
        
        // 设置按钮点击事件
        holder.acceptButton.setOnClickListener {
            onAccept(user)
        }
        
        holder.rejectButton.setOnClickListener {
            onReject(user)
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<UserDTO>() {
        override fun areItemsTheSame(oldItem: UserDTO, newItem: UserDTO): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UserDTO, newItem: UserDTO): Boolean {
            return oldItem == newItem
        }
    }
} 