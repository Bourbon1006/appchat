package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.UserDTO

class ContactSelectionAdapter(
    contacts: List<UserDTO> = emptyList(),
    private val onContactClick: ((UserDTO) -> Unit)? = null
) : RecyclerView.Adapter<ContactSelectionAdapter.ViewHolder>() {
    
    private var contacts: List<UserDTO> = contacts
    private val selectedContacts = mutableSetOf<UserDTO>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val username: TextView = view.findViewById(R.id.username)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.username.text = contact.username
        holder.checkbox.isChecked = selectedContacts.contains(contact)

        // 设置点击事件
        holder.itemView.setOnClickListener {
            if (onContactClick != null) {
                onContactClick.invoke(contact)
            } else {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
                if (holder.checkbox.isChecked) {
                    selectedContacts.add(contact)
                } else {
                    selectedContacts.remove(contact)
                }
            }
        }

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedContacts.add(contact)
            } else {
                selectedContacts.remove(contact)
            }
        }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<UserDTO>) {
        contacts = newContacts
        notifyDataSetChanged()
    }

    fun getSelectedContacts(): List<UserDTO> {
        return selectedContacts.toList()
    }
} 