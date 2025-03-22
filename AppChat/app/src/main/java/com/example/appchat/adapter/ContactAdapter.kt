package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.appchat.R
import com.example.appchat.model.Contact

class ContactAdapter(
    private val contacts: MutableList<Contact>,
    private val onItemClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.contactName)
        val statusIndicator: View = view.findViewById(R.id.statusIndicator)
        val avatarImageView: ImageView = view.findViewById(R.id.contactAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        
        // 设置联系人名称
        holder.nameTextView.text = contact.nickname ?: contact.username
        
        // 设置在线状态
        val statusColor = when (contact.onlineStatus) {
            1 -> R.color.online       // 在线
            2 -> R.color.busy_red     // 忙碌
            else -> R.color.offline   // 离线
        }
        holder.statusIndicator.setBackgroundResource(statusColor)
        
        // 获取服务器 URL
        val serverIp = holder.view.context.getString(R.string.server_ip)
        val serverPort = holder.view.context.getString(R.string.server_port)
        val baseUrl = holder.view.context.getString(R.string.server_url_format, serverIp, serverPort)

        // 构建头像 URL
        val fullUrl = when {
            contact.avatarUrl?.startsWith("http") == true -> contact.avatarUrl
            !contact.avatarUrl.isNullOrEmpty() -> "$baseUrl${contact.avatarUrl}"
            else -> "$baseUrl/api/users/${contact.id}/avatar"  // 使用用户 ID 特定的头像端点
        }

        // 加载头像
        Glide.with(holder.view.context)
            .load(fullUrl)
            .skipMemoryCache(true)  // 跳过内存缓存
            .diskCacheStrategy(DiskCacheStrategy.NONE)  // 跳过磁盘缓存
            .circleCrop()
            .placeholder(R.drawable.default_avatar)
            .error(R.drawable.default_avatar)
            .into(holder.avatarImageView)
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            println("📱 Contact clicked in adapter: ${contact.id}")
            onItemClick(contact)
        }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<Contact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }
} 