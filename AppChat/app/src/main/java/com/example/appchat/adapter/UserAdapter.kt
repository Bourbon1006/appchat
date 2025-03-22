package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.UserDTO

class UserAdapter(
    private var users: List<UserDTO> = emptyList(),
    private val onUserClick: (UserDTO) -> Unit
) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {
    
    fun updateUsers(newUsers: List<UserDTO>) {
        users = newUsers
        notifyDataSetChanged()
    }

    fun updateUserStatus(updatedUser: UserDTO) {
        val position = users.indexOfFirst { it.id == updatedUser.id }
        if (position != -1) {
            users = users.toMutableList().apply {
                this[position] = updatedUser
            }
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)
        holder.itemView.setOnClickListener { onUserClick(user) }
    }

    override fun getItemCount() = users.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userName: TextView = itemView.findViewById(R.id.userName)
        private val userStatus: TextView = itemView.findViewById(R.id.userStatus)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)

        fun bind(user: UserDTO) {
            userName.text = user.username
            userStatus.text = if (user.onlineStatus == 1) "在线" else "离线"
            userStatus.setTextColor(
                itemView.context.getColor(
                    if (user.onlineStatus == 1) android.R.color.holo_green_dark
                    else android.R.color.darker_gray
                )
            )
            updateStatusIndicator(this, user)
        }

        private fun updateStatusIndicator(holder: ViewHolder, user: UserDTO) {
            val isOnline = user.onlineStatus == 1
            holder.statusIndicator.setBackgroundResource(
                if (isOnline) R.drawable.status_online else R.drawable.status_offline
            )
        }
    }
} 