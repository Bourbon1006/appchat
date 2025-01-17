package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.User

class ContactAdapter(
    private val onContactClick: (User) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {
    
    private val contacts = mutableListOf<User>()

    fun updateContacts(newContacts: List<User>) {
        println("Updating contacts: ${newContacts.size} items")
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        println("Creating contact view holder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        println("Binding contact: ${contact.username}")
        holder.bind(contact)
        holder.itemView.setOnClickListener { 
            println("Contact clicked: ${contact.username}")
            onContactClick(contact) 
        }
    }

    override fun getItemCount(): Int {
        println("Contact count: ${contacts.size}")
        return contacts.size
    }

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userName: TextView = itemView.findViewById(R.id.userName)
        private val userStatus: TextView = itemView.findViewById(R.id.userStatus)

        fun bind(user: User) {
            userName.text = user.username
            userStatus.text = if (user.isOnline) "在线" else "离线"
            userStatus.setTextColor(
                itemView.context.getColor(
                    if (user.isOnline) android.R.color.holo_green_dark
                    else android.R.color.darker_gray
                )
            )
        }
    }
} 