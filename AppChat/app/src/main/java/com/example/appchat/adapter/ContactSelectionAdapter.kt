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
    private var contacts: List<UserDTO>,
    private val onContactClick: (UserDTO) -> Unit
) : RecyclerView.Adapter<ContactSelectionAdapter.ViewHolder>() {

    private val selectedContacts = mutableSetOf<UserDTO>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.userName)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.userName.text = contact.username
        holder.checkbox.isChecked = selectedContacts.contains(contact)

        holder.itemView.setOnClickListener {
            if (selectedContacts.contains(contact)) {
                selectedContacts.remove(contact)
            } else {
                selectedContacts.add(contact)
            }
            notifyItemChanged(position)
        }

        holder.checkbox.setOnClickListener {
            if (holder.checkbox.isChecked) {
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

    fun getSelectedContacts() = selectedContacts.toList()
} 