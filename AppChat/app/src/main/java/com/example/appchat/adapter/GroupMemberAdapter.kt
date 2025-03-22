package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appchat.R
import com.example.appchat.databinding.ItemGroupMemberBinding
import com.example.appchat.model.UserDTO
import androidx.core.view.isVisible

class GroupMemberAdapter(
    private val members: List<UserDTO>,
    private val isAdmin: Boolean,
    private val onMemberAction: (UserDTO, String) -> Unit
) : RecyclerView.Adapter<GroupMemberAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemGroupMemberBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupMemberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]
        
        holder.binding.apply {
            memberNameText.text = member.nickname ?: member.username
            
            // 加载头像
            Glide.with(memberAvatar.context)
                .load(member.avatarUrl ?: R.drawable.default_avatar)
                .circleCrop()
                .into(memberAvatar)
            
            // 显示管理员标记
            adminBadge.isVisible = member.isAdmin == true
            
            // 仅当当前用户是管理员且被操作用户不是管理员时显示删除按钮
            removeButton.isVisible = isAdmin && member.isAdmin != true
            
            removeButton.setOnClickListener {
                onMemberAction(member, "REMOVE")
            }
        }
    }

    override fun getItemCount() = members.size
} 