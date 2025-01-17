package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.model.User

class SearchUserAdapter(
    private val currentUserId: Long,
    private val onAddFriend: (User) -> Unit
) : RecyclerView.Adapter<SearchUserAdapter.UserViewHolder>() {
    
    private val users = mutableListOf<User>()

    fun updateUsers(newUsers: List<User>) {
        users.clear()
        users.addAll(newUsers.filter { it.id != currentUserId })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        return UserViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_user, parent, false)
        )
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)
        holder.addButton.setOnClickListener { onAddFriend(user) }
    }

    override fun getItemCount() = users.size

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userName: TextView = itemView.findViewById(R.id.userName)
        val addButton: Button = itemView.findViewById(R.id.addButton)

        fun bind(user: User) {
            userName.text = user.username
        }
    }
} 