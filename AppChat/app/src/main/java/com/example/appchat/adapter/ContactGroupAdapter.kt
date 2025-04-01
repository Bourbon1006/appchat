package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.databinding.ItemContactGroupBinding
import com.example.appchat.model.ContactGroup
import com.example.appchat.model.Contact
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import com.example.appchat.R

class ContactGroupAdapter(
    private val groups: List<ContactGroup>,
    private val onContactClick: (Contact) -> Unit,
    private val onContactLongClick: (Contact) -> Unit,
    private val onGroupLongClick: (ContactGroup) -> Unit
) : RecyclerView.Adapter<ContactGroupAdapter.GroupViewHolder>() {

    private val expandedGroups = mutableSetOf<Long>()  // 记录展开状态的组ID

    inner class GroupViewHolder(private val binding: ItemContactGroupBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        private val contactsAdapter = ContactAdapter(
            onContactClick = onContactClick,
            onContactLongClick = onContactLongClick
        )

        init {
            binding.contactsList.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = contactsAdapter
            }
        }

        fun bind(group: ContactGroup) {
            binding.apply {
                // 设置组名和联系人数量
                groupName.text = group.name
                contactCount.text = "(${group.contacts.size})"

                // 设置展开状态
                val isExpanded = expandedGroups.contains(group.id)
                contactsList.visibility = if (isExpanded) View.VISIBLE else View.GONE
                expandIcon.setImageResource(
                    if (isExpanded) R.drawable.ic_expand_less
                    else R.drawable.ic_expand_more
                )

                // 设置联系人列表
                contactsAdapter.submitList(group.contacts)

                // 设置点击事件
                groupHeader.setOnClickListener {
                    if (isExpanded) {
                        expandedGroups.remove(group.id)
                    } else {
                        expandedGroups.add(group.id)
                    }
                    notifyItemChanged(adapterPosition)
                }

                // 设置长按事件
                groupHeader.setOnLongClickListener {
                    onGroupLongClick(group)
                    true
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemContactGroupBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount() = groups.size
}