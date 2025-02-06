package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.nameText.text = user.nickname ?: user.username
        holder.statusText.text = if (user.isOnline) "在线" else "离线"
        holder.itemView.setOnClickListener { onUserClick(user) }
    }

    override fun getItemCount() = users.size
} 