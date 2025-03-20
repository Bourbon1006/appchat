package com.example.appchat.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appchat.MainActivity
import com.example.appchat.adapter.FriendRequestAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.databinding.DialogFriendRequestsBinding
import com.example.appchat.fragment.ContactsFragment
import com.example.appchat.model.FriendRequest
import com.example.appchat.util.UserPreferences
import kotlinx.coroutines.launch

class FriendRequestsDialog : DialogFragment() {
    private lateinit var binding: DialogFriendRequestsBinding
    private val requests = mutableListOf<FriendRequest>()
    private lateinit var adapter: FriendRequestAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogFriendRequestsBinding.inflate(inflater, container, false)
        
        setupRecyclerView()
        loadPendingRequests()
        
        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = FriendRequestAdapter(
            onAccept = { request -> handleRequest(request.id, true) },
            onReject = { request -> handleRequest(request.id, false) }
        )
        binding.requestsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FriendRequestsDialog.adapter
        }
    }

    private fun loadPendingRequests() {
        lifecycleScope.launch {
            try {
                val userId = UserPreferences.getUserId(requireContext())
                val response = ApiClient.apiService.getPendingRequests(userId)
                if (response.isSuccessful) {
                    response.body()?.let { requests ->
                        adapter.submitList(requests)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "加载好友请求失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleRequest(requestId: Long, accept: Boolean) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.handleFriendRequest(requestId, accept)
                if (response.isSuccessful) {
                    loadPendingRequests() // 重新加载列表
                    // 通知 MainActivity 更新计数
                    (activity as? MainActivity)?.loadPendingRequests()
                } else {
                    Toast.makeText(requireContext(), "处理请求失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 