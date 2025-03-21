package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.Contact
import com.example.appchat.util.loadAvatar

class ContactAdapter(
    private var contacts: List<Contact>,
    private val onItemClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarImage: ImageView = view.findViewById(R.id.avatarImage)
        val nameText: TextView = view.findViewById(R.id.nameText)
        val statusIndicator: View = view.findViewById(R.id.statusIndicator)
        val statusText: TextView = view.findViewById(R.id.statusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        
        // 使用 nickname 而不是 username
        holder.nameText.text = contact.nickname ?: contact.username
        
        // 设置头像和在线状态
        holder.avatarImage.loadAvatar(contact.avatarUrl)
        
        // 设置在线状态
        when (contact.onlineStatus) {
            0 -> { // 离线
                holder.statusIndicator.setBackgroundResource(R.drawable.status_indicator_offline)
                holder.statusText.text = "离线"
            }
            1 -> { // 在线
                holder.statusIndicator.setBackgroundResource(R.drawable.status_indicator_online)
                holder.statusText.text = "在线"
            }
            2 -> { // 忙碌
                holder.statusIndicator.setBackgroundResource(R.drawable.status_indicator_busy)
                holder.statusText.text = "忙碌"
            }
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(contact)
        }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<Contact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
} 