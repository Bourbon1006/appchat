package com.example.appchat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.databinding.ItemFriendRequestBinding
import com.example.appchat.model.FriendRequest
import com.example.appchat.util.loadAvatar

class FriendRequestAdapter(
    private val onAccept: (FriendRequest) -> Unit,
    private val onReject: (FriendRequest) -> Unit
) : ListAdapter<FriendRequest, FriendRequestAdapter.ViewHolder>(FriendRequestDiffCallback()) {

    class ViewHolder(
        private val binding: ItemFriendRequestBinding,
        private val onAccept: (FriendRequest) -> Unit,
        private val onReject: (FriendRequest) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(request: FriendRequest) {
            binding.apply {
                userName.text = request.sender.username
                userAvatar.loadAvatar(request.sender.avatarUrl)
                
                acceptButton.setOnClickListener { onAccept(request) }
                rejectButton.setOnClickListener { onReject(request) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemFriendRequestBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onAccept,
            onReject
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
} 