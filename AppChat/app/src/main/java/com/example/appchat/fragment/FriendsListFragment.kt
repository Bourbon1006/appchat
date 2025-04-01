package com.example.appchat.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appchat.R
import com.example.appchat.activity.ChatActivity
import com.example.appchat.adapter.ContactGroupAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.databinding.FragmentFriendsListBinding
import com.example.appchat.model.Contact
import com.example.appchat.model.ContactGroup
import com.example.appchat.util.UserPreferences
import com.example.appchat.websocket.WebSocketManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class FriendsListFragment : Fragment() {
    private var _binding: FragmentFriendsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var groupAdapter: ContactGroupAdapter
    private val groups = mutableListOf<ContactGroup>()

    // 定义接口
    interface ContactClickListener {
        fun onContactClick(contact: Contact)
    }
    
    private var contactClickListener: ContactClickListener? = null

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
        setupContactList()
        loadContacts()
        setupWebSocketListener()

        // 添加创建分组按钮
        binding.fabCreateGroup.setOnClickListener {
            showCreateGroupDialog()
        }
    }

    private fun setupWebSocketListener() {
        // 注册 WebSocket 监听器，接收联系人状态更新
        WebSocketManager.addOnlineStatusListener { userId, status ->
            // 在联系人列表中查找对应的联系人，并更新其状态
            val updatedGroups = groups.map { group ->
                val updatedContacts = group.contacts.map { contact ->
                    if (contact.id == userId) {
                        contact.copy(onlineStatus = status)
                    } else {
                        contact
                    }
                }.toMutableList()
                group.copy(contacts = updatedContacts)
            }

            // 更新适配器
            activity?.runOnUiThread {
                groups.clear()
                groups.addAll(updatedGroups)
                groupAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupContactList() {
        groupAdapter = ContactGroupAdapter(
            groups = groups,
            onContactClick = { contact ->
                showContactActions(contact)
            },
            onContactLongClick = { contact ->
                showContactActions(contact)
            },
            onGroupLongClick = { group ->
                if (group.groupType != ContactGroup.TYPE_DEFAULT) {
                    showGroupManagementDialog(group)
                }
            }
        )

        binding.contactsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = groupAdapter
        }
    }

    private fun showContactActions(contact: Contact) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_contact_actions, null)
        
        // 设置联系人名称
        view.findViewById<TextView>(R.id.contactNameTitle).text = contact.nickname ?: contact.username
        
        // 发送消息
        view.findViewById<View>(R.id.chatAction).setOnClickListener {
            dialog.dismiss()
            startChat(contact)
        }
        
        // 查看资料
        view.findViewById<View>(R.id.viewProfileAction).setOnClickListener {
            dialog.dismiss()
            viewProfile(contact)
        }
        
        // 删除好友
        view.findViewById<View>(R.id.deleteAction).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmDialog(contact)
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun startChat(contact: Contact) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("contactId", contact.id)
            putExtra("contactName", contact.nickname ?: contact.username)
        }
        startActivity(intent)
    }

    private fun viewProfile(contact: Contact) {
        // TODO: 实现查看资料功能
    }

    private fun showDeleteConfirmDialog(contact: Contact) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除好友")
            .setMessage("确定要删除好友\"${contact.nickname ?: contact.username}\"吗？")
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
                deleteContact(contact)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteContact(contact: Contact) {
        val userId = UserPreferences.getUserId(requireContext())
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.deleteFriend(userId = userId, friendId = contact.id)
                if (response.isSuccessful) {
                    Toast.makeText(context, "已删除好友", Toast.LENGTH_SHORT).show()
                    loadContacts()
                } else {
                    Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshContacts() {
        println("📝 FriendsListFragment.refreshContacts() called")
        loadContacts()
    }

    private fun showGroupManagementDialog(group: ContactGroup) {
        val options = arrayOf("重命名分组", "删除分组", "移动联系人")
        AlertDialog.Builder(requireContext())
            .setTitle("分组管理")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(group)
                    1 -> showDeleteGroupConfirmDialog(group)
                    2 -> showMoveContactsDialog(group)
                }
            }
            .show()
    }

    private fun showRenameGroupDialog(group: ContactGroup) {
        val input = EditText(requireContext())
        input.setText(group.name)
        AlertDialog.Builder(requireContext())
            .setTitle("重命名分组")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    // TODO: 调用API更新分组名称
                    loadContacts()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteGroupConfirmDialog(group: ContactGroup) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除分组")
            .setMessage("确定要删除分组${group.name}？分组内的联系人将被移动到我的好友组。")
            .setPositiveButton("确定") { _, _ ->
                // TODO: 调用API删除分组
                loadContacts()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showMoveContactsDialog(sourceGroup: ContactGroup) {
        val targetGroups = groups.filter { it.id != sourceGroup.id && it.groupType != ContactGroup.TYPE_DEFAULT }
        val groupNames = targetGroups.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("选择目标分组")
            .setItems(groupNames) { _, which ->
                val targetGroup = targetGroups[which]
                // TODO: 调用API移动联系人
                loadContacts()
            }
            .show()
    }

    private fun showCreateGroupDialog() {
        val input = EditText(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle("创建新分组")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val groupName = input.text.toString().trim()
                if (groupName.isNotEmpty()) {
                    // TODO: 调用API创建新分组
                    loadContacts()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadContacts() {
        val userId = UserPreferences.getUserId(requireContext())
        println("📝 FriendsListFragment.loadContacts() for userId: $userId")

        lifecycleScope.launch {
            try {
                println("🔄 Making API call to load contacts")
                val response = ApiClient.apiService.getFriends(userId)
                println("📡 Got response: ${response.isSuccessful}, code: ${response.code()}")

                if (response.isSuccessful) {
                    response.body()?.let { userList ->
                        println("✅ FriendsListFragment loaded ${userList.size} contacts")
                        activity?.runOnUiThread {
                            println("🔄 Updating contacts adapter with ${userList.size} items")

                            // 创建默认分组
                            val onlineGroup = ContactGroup(
                                id = 1,
                                name = "在线好友",
                                groupType = ContactGroup.TYPE_DEFAULT,
                                contacts = mutableListOf()
                            )
                            val offlineGroup = ContactGroup(
                                id = 2,
                                name = "离线好友",
                                groupType = ContactGroup.TYPE_DEFAULT,
                                contacts = mutableListOf()
                            )
                            val myFriendsGroup = ContactGroup(
                                id = 3,
                                name = "我的好友组",
                                groupType = ContactGroup.TYPE_MY_FRIENDS,
                                creatorId = userId,
                                contacts = mutableListOf()
                            )

                            // TODO: 从API获取用户自定义分组
                            val customGroups = mutableListOf<ContactGroup>()

                            userList.forEach { user ->
                                val contact = Contact(
                                    id = user.id,
                                    username = user.username,
                                    nickname = user.nickname,
                                    avatarUrl = user.avatarUrl,
                                    onlineStatus = user.onlineStatus ?: 0
                                )

                                // 按在线状态显示
                                if (contact.onlineStatus > 0) {
                                    onlineGroup.contacts.add(contact)
                                } else {
                                    offlineGroup.contacts.add(contact)
                                }

                                // 同时添加到"我的好友"分组
                                myFriendsGroup.contacts.add(contact)
                            }

                            // 更新分组列表
                            groups.clear()
                            groups.addAll(listOf(onlineGroup, offlineGroup, myFriendsGroup) + customGroups)
                            groupAdapter.notifyDataSetChanged()
                        }
                    }
                } else {
                    println("❌ Failed to load contacts: ${response.code()}")
                    Toast.makeText(context, "加载联系人失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error loading contacts: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // 移除 WebSocket 监听器
        WebSocketManager.removeOnlineStatusListener()
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    // 提供设置监听器的方法
    fun setContactClickListener(listener: ContactClickListener) {
        contactClickListener = listener
    }
}