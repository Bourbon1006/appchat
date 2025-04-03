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
import kotlinx.coroutines.async

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
                val updatedContacts = group.contacts?.map { contact ->
                    if (contact.id == userId) {
                        contact.copy(onlineStatus = status)
                    } else {
                        contact
                    }
                }?.toMutableList()
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
                startChat(contact)
            },
            onContactLongClick = { contact, groupId ->
                showContactActionsDialog(contact, groupId)
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
            setHasFixedSize(true)
        }
    }

    private fun showContactActionsDialog(contact: Contact, groupId: Long) {
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

        // 移动分组（在"我的好友"分组或自定义分组中显示）
        val moveToGroupAction = view.findViewById<View>(R.id.moveToGroupAction)
        if (groupId > 0 || groupId == -1L) {  // 自定义分组或"我的好友"分组
            moveToGroupAction.visibility = View.VISIBLE
            moveToGroupAction.setOnClickListener {
                dialog.dismiss()
                showMoveContactDialog(contact, groupId)
            }
        } else {
            moveToGroupAction.visibility = View.GONE
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
            putExtra("chat_type", "PRIVATE")  // 指定是私聊
            putExtra("receiver_id", contact.id)  // 接收者ID
            putExtra("receiver_name", contact.nickname ?: contact.username)  // 接收者名称
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
        println("📝 Showing group management dialog for: ${group.name}")
        val options = arrayOf("重命名分组", "删除分组")
        AlertDialog.Builder(requireContext())
            .setTitle("分组管理")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(group)
                    1 -> showDeleteGroupConfirmDialog(group)
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
                    renameFriendGroup(group.id, newName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun renameFriendGroup(groupId: Long, newName: String) {
        val userId = UserPreferences.getUserId(requireContext())
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.updateFriendGroupName(groupId, userId, newName)
                if (response.isSuccessful) {
                    Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show()
                    loadContacts()
                } else {
                    Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteGroupConfirmDialog(group: ContactGroup) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除分组")
            .setMessage("确定要删除分组\"${group.name}\"吗？分组内的联系人将被移动到默认分组。")
            .setPositiveButton("确定") { _, _ ->
                deleteFriendGroup(group.id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteFriendGroup(groupId: Long) {
        val userId = UserPreferences.getUserId(requireContext())
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.deleteFriendGroup(groupId, userId)
                if (response.isSuccessful) {
                    Toast.makeText(context, "删除分组成功", Toast.LENGTH_SHORT).show()
                    loadContacts()
                } else {
                    Toast.makeText(context, "删除分组失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMoveContactDialog(contact: Contact, sourceGroupId: Long) {
        val userId = UserPreferences.getUserId(requireContext())
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getFriendGroups(userId)
                if (response.isSuccessful) {
                    // 获取所有自定义分组
                    val groups = response.body()!!.filter { group -> 
                        // 如果是从"我的好友"分组移动，显示所有自定义分组
                        // 如果是从自定义分组移动，不显示当前分组
                        if (sourceGroupId == -1L) {
                            true  // 显示所有自定义分组
                        } else {
                            group.id != sourceGroupId  // 不显示当前分组
                        }
                    }
                    
                    if (groups.isEmpty()) {
                        Toast.makeText(context, "没有可用的目标分组，请先创建分组", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    
                    val groupNames = groups.map { it.name }.toTypedArray()
                    
                    AlertDialog.Builder(requireContext())
                        .setTitle("选择目标分组")
                        .setItems(groupNames) { _, which ->
                            val targetGroup = groups[which]
                            if (sourceGroupId == -1L) {  // 从"我的好友"分组移动
                                moveContactToGroup(contact.id, null, targetGroup.id)
                            } else {  // 从自定义分组移动
                                moveContactToGroup(contact.id, sourceGroupId, targetGroup.id)
                            }
                        }
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "获取分组列表失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun moveContactToGroup(contactId: Long, sourceGroupId: Long?, targetGroupId: Long) {
        val userId = UserPreferences.getUserId(requireContext())
        lifecycleScope.launch {
            try {
                // 如果是从自定义分组移动，先移除
                if (sourceGroupId != null) {
                    ApiClient.apiService.removeFriendFromGroup(sourceGroupId, contactId, userId)
                }
                
                // 添加到目标分组
                val response = ApiClient.apiService.addFriendToGroup(targetGroupId, contactId, userId)
                
                if (response.isSuccessful) {
                    Toast.makeText(context, "移动成功", Toast.LENGTH_SHORT).show()
                    loadContacts()
                } else {
                    Toast.makeText(context, "移动失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateGroupDialog() {
        val input = EditText(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle("创建新分组")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val groupName = input.text.toString().trim()
                if (groupName.isNotEmpty()) {
                    createFriendGroup(groupName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createFriendGroup(name: String) {
        val userId = UserPreferences.getUserId(requireContext())
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.createFriendGroup(userId, name)
                if (response.isSuccessful) {
                    Toast.makeText(context, "创建分组成功", Toast.LENGTH_SHORT).show()
                    loadContacts()
                } else {
                    Toast.makeText(context, "创建分组失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadContacts() {
        val userId = UserPreferences.getUserId(requireContext())
        println("📝 FriendsListFragment.loadContacts() for userId: $userId")

        lifecycleScope.launch {
            try {
                // 并行加载联系人和分组信息
                val contactsDeferred = lifecycleScope.async { 
                    ApiClient.apiService.getFriends(userId) 
                }
                val groupsDeferred = lifecycleScope.async { 
                    ApiClient.apiService.getFriendGroups(userId) 
                }

                val contactsResponse = contactsDeferred.await()
                val groupsResponse = groupsDeferred.await()

                if (contactsResponse.isSuccessful && groupsResponse.isSuccessful) {
                    val contacts = contactsResponse.body()!!
                    val serverGroups = groupsResponse.body()!!
                    
                    println("📝 Server returned groups: ${serverGroups.map { it.name to it.members?.size }}")
                    
                    val customGroups = serverGroups.map { group ->
                        val groupContacts = group.members?.map { member ->
                            Contact(
                                id = member.id,
                                username = member.username,
                                nickname = member.nickname,
                                avatarUrl = member.avatarUrl,
                                onlineStatus = member.onlineStatus ?: 0
                            )
                        }?.toMutableList() ?: mutableListOf()

                        println("📝 Processing group ${group.name} with ${groupContacts.size} members")

                        ContactGroup(
                            id = group.id,
                            name = group.name,
                            groupType = ContactGroup.TYPE_CUSTOM,
                            contacts = groupContacts
                        )
                    }

                    // 创建默认分组
                    val myFriendsGroup = ContactGroup(
                        id = -1,
                        name = "我的好友",
                        groupType = ContactGroup.TYPE_MY_FRIENDS,
                        contacts = mutableListOf()
                    )
                    val onlineGroup = ContactGroup(
                        id = -2,
                        name = "在线好友",
                        groupType = ContactGroup.TYPE_DEFAULT,
                        contacts = mutableListOf()
                    )
                    val offlineGroup = ContactGroup(
                        id = -3,
                        name = "离线好友",
                        groupType = ContactGroup.TYPE_DEFAULT,
                        contacts = mutableListOf()
                    )

                    // 将联系人转换为 Contact 对象
                    val allContacts = contacts.map { user ->
                        Contact(
                            id = user.id,
                            username = user.username,
                            nickname = user.nickname,
                            avatarUrl = user.avatarUrl,
                            onlineStatus = user.onlineStatus ?: 0
                        )
                    }

                    // 分配联系人到默认分组
                    allContacts.forEach { contact ->
                        // 添加到"我的好友"分组
                        myFriendsGroup.contacts?.add(contact)
                        
                        // 同时根据在线状态添加到对应分组
                        if (contact.onlineStatus > 0) {
                            onlineGroup.contacts?.add(contact)
                        } else {
                            offlineGroup.contacts?.add(contact)
                        }
                    }

                    activity?.runOnUiThread {
                        // 清除并更新分组列表
                        this@FriendsListFragment.groups.clear()
                        this@FriendsListFragment.groups.add(myFriendsGroup)  // 添加"我的好友"分组
                        this@FriendsListFragment.groups.addAll(customGroups)  // 添加自定义分组
                        this@FriendsListFragment.groups.add(onlineGroup)      // 添加在线分组
                        this@FriendsListFragment.groups.add(offlineGroup)     // 添加离线分组

                        // 更新适配器
                        groupAdapter.notifyDataSetChanged()
                    }
                } else {
                    Toast.makeText(context, "加载失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
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