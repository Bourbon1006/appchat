package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.UserDTO

class SearchUserAdapter(
    private var users: List<UserDTO>,
    private val onUserClick: (UserDTO) -> Unit
) : RecyclerView.Adapter<SearchUserAdapter.ViewHolder>() {

    fun updateContacts(newUsers: List<UserDTO>) {
        users = newUsers
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.userName)
        val statusText: TextView = view.findViewById(R.id.userStatus)
        val addFriendButton: Button = view.findViewById(R.id.addFriendButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.nameText.text = user.nickname ?: user.username
        val statusColor = if (user.onlineStatus == 1) {
            ContextCompat.getColor(holder.itemView.context, R.color.online)
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.offline)
        }
        holder.statusText.setTextColor(statusColor)
        holder.statusText.text = if (user.onlineStatus == 1) "在线" else "离线"
        holder.addFriendButton.setOnClickListener { onUserClick(user) }
    }

    override fun getItemCount() = users.size
}