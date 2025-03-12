package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appchat.R
import com.example.appchat.databinding.ItemContactBinding
import com.example.appchat.model.Contact

class ContactAdapter(
    private val contacts: List<Contact>,
    private val onItemClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.binding.apply {
            contactName.text = contact.name
            Glide.with(root.context)
                .load(contact.avatarUrl)
                .placeholder(R.drawable.default_avatar)
                .into(contactAvatar)
            root.setOnClickListener { onItemClick(contact) }
        }
    }

    override fun getItemCount() = contacts.size
} 