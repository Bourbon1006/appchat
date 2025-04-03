package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appchat.R
import com.example.appchat.databinding.ItemGroupMemberBinding
import com.example.appchat.model.UserDTO
import androidx.core.view.isVisible
import com.example.appchat.util.loadAvatar

class GroupMemberAdapter(
    private val members: List<UserDTO>,
    private val isAdmin: Boolean,
    private val onMemberAction: (UserDTO, String) -> Unit
) : RecyclerView.Adapter<GroupMemberAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.memberAvatar)
        val username: TextView = view.findViewById(R.id.memberUsername)
        val removeButton: ImageButton = view.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]
        
        // 设置用户名
        holder.username.text = member.username
        
        // 加载头像
        val baseUrl = holder.itemView.context.getString(
            R.string.server_url_format,
            holder.itemView.context.getString(R.string.server_ip),
            holder.itemView.context.getString(R.string.server_port)
        )
        val avatarUrl = "$baseUrl/api/users/${member.id}/avatar"
        
        // 使用扩展函数加载头像
        holder.avatar.loadAvatar(avatarUrl)
        
        // 设置移除按钮可见性和点击事件
        holder.removeButton.visibility = if (isAdmin) View.VISIBLE else View.GONE
        holder.removeButton.setOnClickListener {
            onMemberAction(member, "REMOVE")
        }
    }

    override fun getItemCount() = members.size
} 