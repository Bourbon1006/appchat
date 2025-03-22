package com.example.appchat.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appchat.R
import com.example.appchat.adapter.FriendRequestAdapter
import com.example.appchat.databinding.DialogFriendRequestsBinding
import com.example.appchat.model.UserDTO
import kotlinx.coroutines.CoroutineScope

class FriendRequestsDialog(
    context: Context,
    private val onAccept: (UserDTO) -> Unit,
    private val onReject: (UserDTO) -> Unit,
    private val coroutineScope: CoroutineScope
) : Dialog(context) {

    private lateinit var binding: DialogFriendRequestsBinding
    private lateinit var adapter: FriendRequestAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = DialogFriendRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置对话框宽度
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        setupRecyclerView()
        setupCloseButton()
    }
    
    private fun setupRecyclerView() {
        adapter = FriendRequestAdapter(
            onAccept = { user ->
                onAccept(user)
            },
            onReject = { user ->
                onReject(user)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FriendRequestsDialog.adapter
        }
    }
    
    private fun setupCloseButton() {
        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }
    
    fun updateRequests(requests: List<UserDTO>) {
        if (::adapter.isInitialized) {
            adapter.submitList(requests)
            // 如果没有请求，关闭对话框
            if (requests.isEmpty()) {
                dismiss()
            }
        }
    }
} 