package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appchat.R
import com.example.appchat.model.Contact
import com.example.appchat.util.loadAvatar

class ContactAdapter(
    private val contacts: List<Contact>,
    private val onItemClick: (Contact) -> Unit,
    private val onItemLongClick: ((Contact) -> Unit)? = null
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val statusDot: View = view.findViewById(R.id.statusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        
        holder.name.text = contact.name
        holder.avatar.loadAvatar(contact.avatarUrl)
        
        // 设置在线状态指示器
        holder.statusDot.setBackgroundResource(
            if (contact.isOnline) R.color.online_status 
            else R.color.offline_status
        )

        holder.itemView.setOnClickListener { onItemClick(contact) }
        onItemLongClick?.let { longClick ->
            holder.itemView.setOnLongClickListener { 
                longClick(contact)
                true
            }
        }
    }

    override fun getItemCount() = contacts.size
} 