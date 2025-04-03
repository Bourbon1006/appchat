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
    private val onContactLongClick: (Contact, Long) -> Unit,
    private val onGroupLongClick: (ContactGroup) -> Unit
) : RecyclerView.Adapter<ContactGroupAdapter.GroupViewHolder>() {

    private val expandedGroups = mutableSetOf<Long>()  // 记录展开状态的组ID

    inner class GroupViewHolder(private val binding: ItemContactGroupBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        private val contactsAdapter = ContactAdapter(
            onContactClick = { contact -> 
                // 直接调用外部传入的点击处理函数
                onContactClick(contact)
            },
            onContactLongClick = { contact -> 
                onContactLongClick(contact, groups[adapterPosition].id)
            }
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
                contactCount.text = "(${group.contacts?.size ?: 0})"

                // 设置展开状态
                val isExpanded = expandedGroups.contains(group.id)
                contactsList.visibility = if (isExpanded) View.VISIBLE else View.GONE
                expandIcon.setImageResource(
                    if (isExpanded) R.drawable.ic_expand_less
                    else R.drawable.ic_expand_more
                )

                // 设置联系人列表
                contactsAdapter.submitList(group.contacts ?: emptyList())

                // 设置分组长按事件
                groupHeader.setOnLongClickListener { view ->
                    println("�� Group long clicked: ${group.name}, type: ${group.groupType}")
                    if (group.groupType != ContactGroup.TYPE_DEFAULT) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)  // 添加触感反馈
                        onGroupLongClick(group)
                        true
                    } else {
                        false
                    }
                }

                // 设置分组点击事件
                groupHeader.setOnClickListener { view ->
                    println("📝 Group clicked: ${group.name}")
                    val isExpanded = expandedGroups.contains(group.id)
                    if (isExpanded) {
                        expandedGroups.remove(group.id)
                    } else {
                        expandedGroups.add(group.id)
                    }
                    notifyItemChanged(adapterPosition)
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