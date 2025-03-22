package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.databinding.ItemFriendRequestBinding
import com.example.appchat.model.UserDTO
import com.bumptech.glide.Glide

class FriendRequestAdapter(
    private val onAccept: (UserDTO) -> Unit,
    private val onReject: (UserDTO) -> Unit
) : ListAdapter<UserDTO, FriendRequestAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(
        private val binding: ItemFriendRequestBinding,
        private val onAccept: (UserDTO) -> Unit,
        private val onReject: (UserDTO) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(user: UserDTO) {
            binding.userName.text = user.nickname ?: user.username
            
            // 加载头像
            Glide.with(binding.root.context)
                .load(user.avatarUrl)
                .circleCrop()
                .into(binding.userAvatar)
            
            // 设置按钮点击事件
            binding.acceptButton.setOnClickListener { onAccept(user) }
            binding.rejectButton.setOnClickListener { onReject(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onAccept, onReject)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffCallback : DiffUtil.ItemCallback<UserDTO>() {
        override fun areItemsTheSame(oldItem: UserDTO, newItem: UserDTO) = 
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UserDTO, newItem: UserDTO) = 
            oldItem == newItem
    }
} 