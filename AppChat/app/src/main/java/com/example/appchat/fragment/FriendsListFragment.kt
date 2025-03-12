package com.example.appchat.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.ChatActivity
import com.example.appchat.R
import com.example.appchat.adapter.ContactAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.databinding.FragmentFriendsListBinding
import com.example.appchat.model.Contact
import com.example.appchat.model.UserDTO
import com.example.appchat.util.UserPreferences
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FriendsListFragment : Fragment() {
    private var _binding: FragmentFriendsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ContactAdapter
    private val contacts = mutableListOf<Contact>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ContactAdapter(
            contacts = contacts,
            onItemClick = { contact ->
                navigateToChat(contact.id, contact.name, contact.avatarUrl)
            }
        )
        binding.contactsList.adapter = adapter
        binding.contactsList.layoutManager = LinearLayoutManager(context)

        loadContacts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun navigateToChat(userId: Long, userName: String, userAvatar: String) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("receiver_id", userId)
            putExtra("receiver_name", userName)
            putExtra("partnerAvatar", userAvatar)
            putExtra("chat_type", "PRIVATE")
        }
        startActivity(intent)
    }

    private fun loadContacts() {
        val userId = UserPreferences.getUserId(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getFriends(userId)
                if (response.isSuccessful) {
                    contacts.clear()
                    contacts.addAll(response.body()?.map { user ->
                        Contact(
                            id = user.id,
                            name = user.nickname ?: user.username,
                            avatarUrl = user.avatarUrl ?: "",
                            isOnline = user.isOnline
                        )
                    } ?: emptyList())
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "加载联系人失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFriendOptionsDialog(contact: UserDTO) {
        AlertDialog.Builder(requireContext())
            .setTitle(contact.username)
            .setItems(arrayOf("发送消息", "删除好友")) { _, which ->
                when (which) {
                    0 -> navigateToChat(contact.id, contact.nickname ?: contact.username, contact.avatarUrl ?: "")
                    1 -> showDeleteFriendConfirmDialog(contact)
                }
            }
            .show()
    }

    private fun showDeleteFriendConfirmDialog(contact: UserDTO) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除好友")
            .setMessage("确定要删除好友 ${contact.username} 吗？")
            .setPositiveButton("确定") { _, _ ->
                deleteFriend(contact.id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteFriend(friendId: Long) {
        lifecycleScope.launch {
            try {
                ApiClient.apiService.deleteFriend(
                    userId = UserPreferences.getUserId(requireContext()),
                    friendId = friendId
                )
                Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                loadContacts() // 重新加载好友列表
            } catch (e: Exception) {
                Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 