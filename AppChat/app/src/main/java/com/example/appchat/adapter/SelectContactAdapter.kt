package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appchat.R
import com.example.appchat.databinding.ItemSelectContactBinding
import com.example.appchat.model.UserDTO

class SelectContactAdapter(
    private val contacts: List<UserDTO>,
    private val existingMembers: Set<Long>
) : RecyclerView.Adapter<SelectContactAdapter.ViewHolder>() {

    private val selectedContacts = mutableSetOf<Long>()

    inner class ViewHolder(val binding: ItemSelectContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = contacts.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        val binding = holder.binding

        // 设置用户信息
        binding.contactName.text = contact.username
        
        Glide.with(binding.root)
            .load(contact.avatarUrl ?: R.drawable.default_avatar)
            .circleCrop()
            .into(binding.contactAvatar)

        // 如果是已存在的成员，禁用选择
        if (existingMembers.contains(contact.id)) {
            binding.checkBox.isEnabled = false
            binding.checkBox.isChecked = true
            binding.checkBox.text = "已是成员"
        } else {
            binding.checkBox.isEnabled = true
            binding.checkBox.isChecked = selectedContacts.contains(contact.id)
            binding.checkBox.text = ""
        }

        // 设置点击事件
        binding.root.setOnClickListener {
            if (!existingMembers.contains(contact.id)) {
                binding.checkBox.isChecked = !binding.checkBox.isChecked
                if (binding.checkBox.isChecked) {
                    selectedContacts.add(contact.id)
                } else {
                    selectedContacts.remove(contact.id)
                }
            }
        }

        binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (!existingMembers.contains(contact.id)) {
                if (isChecked) {
                    selectedContacts.add(contact.id)
                } else {
                    selectedContacts.remove(contact.id)
                }
            }
        }
    }

    fun getSelectedContactIds(): List<Long> {
        return selectedContacts.toList()
    }
} 