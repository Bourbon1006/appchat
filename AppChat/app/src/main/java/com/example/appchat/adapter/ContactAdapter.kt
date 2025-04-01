package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.databinding.ItemContactBinding
import com.example.appchat.model.Contact
import com.bumptech.glide.Glide
import com.example.appchat.R

class ContactAdapter(
    private val onContactClick: (Contact) -> Unit,
    private val onContactLongClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {
    
    private var contacts = listOf<Contact>()

    inner class ContactViewHolder(private val binding: ItemContactBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(contact: Contact) {
            binding.apply {
                contactName.text = contact.nickname ?: contact.username
                
                // 设置状态消息
                statusMessage.text = when(contact.onlineStatus) {
                    1 -> "在线"
                    2 -> "忙碌"
                    else -> "离线"
                }
                
                // 设置在线状态指示器颜色
                onlineIndicator.setBackgroundResource(when(contact.onlineStatus) {
                    1 -> R.drawable.online_indicator
                    2 -> R.drawable.busy_indicator
                    else -> R.drawable.offline_indicator
                })
                
                // 加载头像
                Glide.with(root.context)
                    .load(contact.avatarUrl)
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .circleCrop()
                    .into(contactAvatar)

                // 设置点击和长按事件
                root.setOnClickListener { onContactClick(contact) }
                root.setOnLongClickListener { 
                    onContactLongClick(contact)
                    true
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount() = contacts.size

    fun submitList(newContacts: List<Contact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
} 