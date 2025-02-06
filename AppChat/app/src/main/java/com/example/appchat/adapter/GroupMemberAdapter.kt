package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.UserDTO

class GroupMemberAdapter(
    private val members: List<UserDTO>,
    private val currentUserId: Long,
    private val isCreator: Boolean,
    private val onMemberClick: (UserDTO) -> Unit
) : RecyclerView.Adapter<GroupMemberAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.userName)
        val removeButton: ImageButton = view.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]
        holder.userName.text = member.username

        // 只有群主可以移除其他成员
        holder.removeButton.visibility = if (isCreator && member.id != currentUserId) {
            View.VISIBLE
        } else {
            View.GONE
        }

        holder.removeButton.setOnClickListener {
            onMemberClick(member)
        }
    }

    override fun getItemCount() = members.size
} 