package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.databinding.ItemContactBinding
import com.example.appchat.model.Contact
import com.bumptech.glide.Glide
import com.example.appchat.R
import androidx.recyclerview.widget.ListAdapter
import com.example.appchat.util.loadAvatar
import androidx.recyclerview.widget.DiffUtil

class ContactAdapter(
    private val onContactClick: (Contact) -> Unit,
    private val onContactLongClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactAdapter.ViewHolder>(ContactDiffCallback()) {
    
    // 添加 DiffUtil.ItemCallback 实现
    private class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }

    inner class ViewHolder(private val binding: ItemContactBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(contact: Contact) {
            binding.apply {
                // 设置联系人名称
                contactName.text = contact.nickname ?: contact.username
                
                // 设置在线状态
                onlineStatus.setImageResource(
                    when (contact.onlineStatus) {
                        0 -> R.drawable.ic_status_offline
                        1 -> R.drawable.ic_status_online
                        else -> R.drawable.ic_status_busy
                    }
                )
                
                // 加载头像
                contactAvatar.loadAvatar(contact.getFullAvatarUrl(root.context))

                // 设置点击事件
                root.setOnClickListener { onContactClick(contact) }
                root.setOnLongClickListener { 
                    onContactLongClick(contact)
                    true
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
} 