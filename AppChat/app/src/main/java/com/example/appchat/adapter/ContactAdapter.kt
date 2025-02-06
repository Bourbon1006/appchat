package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.appchat.R
import com.example.appchat.model.UserDTO

class ContactAdapter(private val onContactClick: (UserDTO) -> Unit) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {
    private val contacts = mutableListOf<UserDTO>()

    fun updateContacts(newContacts: List<UserDTO>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact)
        holder.itemView.setOnClickListener { onContactClick(contact) }
    }

    override fun getItemCount() = contacts.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatarImage: ImageView = view.findViewById(R.id.contactAvatar)
        private val nameText: TextView = view.findViewById(R.id.contactName)
        private val statusText: TextView = view.findViewById(R.id.contactStatus)

        fun bind(contact: UserDTO) {
            nameText.text = contact.nickname ?: contact.username
            statusText.text = if (contact.isOnline) "在线" else "离线"
            statusText.setTextColor(
                itemView.context.getColor(
                    if (contact.isOnline) android.R.color.holo_green_dark
                    else android.R.color.darker_gray
                )
            )
            
            // 加载用户头像
            val avatarUrl = "${itemView.context.getString(R.string.server_url_format).format(
                itemView.context.getString(R.string.server_ip),
                itemView.context.getString(R.string.server_port)
            )}/api/users/${contact.id}/avatar"
            
            Glide.with(itemView.context)
                .load(avatarUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(avatarImage)
        }
    }
} 