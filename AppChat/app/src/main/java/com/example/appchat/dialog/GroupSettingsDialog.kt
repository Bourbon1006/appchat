package com.example.appchat.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appchat.R
import com.example.appchat.adapter.GroupMemberAdapter
import com.example.appchat.adapter.ContactSelectionAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.model.Group
import com.example.appchat.model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class GroupSettingsDialog(
    private val context: Context,
    private val group: Group,
    private val currentUserId: Long,
    private val onGroupUpdated: (Group) -> Unit
) {
    fun show() {
        val dialog = AlertDialog.Builder(context)
            .setTitle("群组设置")
            .create()

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_group_settings_extended, null)
        val groupNameInput = view.findViewById<EditText>(R.id.groupNameInput)
        val groupAnnouncementInput = view.findViewById<EditText>(R.id.groupAnnouncementInput)
        val memberList = view.findViewById<RecyclerView>(R.id.memberList)
        val addMemberButton = view.findViewById<Button>(R.id.addMemberButton)
        val saveButton = view.findViewById<Button>(R.id.saveButton)

        groupNameInput.setText(group.name)
        groupAnnouncementInput.setText(group.announcement)

        // 只有群主可以修改群设置
        val isCreator = group.creator.id == currentUserId
        groupNameInput.isEnabled = isCreator
        groupAnnouncementInput.isEnabled = isCreator
        addMemberButton.visibility = if (isCreator) View.VISIBLE else View.GONE

        val adapter = GroupMemberAdapter(
            members = group.members,
            currentUserId = currentUserId,
            isCreator = isCreator
        ) { user ->
            if (isCreator) {
                showRemoveMemberConfirmDialog(group.id, user)
            }
        }

        memberList.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }

        addMemberButton.setOnClickListener {
            showAddMemberDialog(group)
        }

        saveButton.setOnClickListener {
            if (isCreator) {
                val updatedGroup = group.copy(
                    name = groupNameInput.text.toString(),
                    announcement = groupAnnouncementInput.text.toString()
                )
                ApiClient.service.updateGroup(group.id, updatedGroup)
                    .enqueue(object : Callback<Group> {
                        override fun onResponse(call: Call<Group>, response: Response<Group>) {
                            if (response.isSuccessful) {
                                response.body()?.let { group ->
                                    onGroupUpdated(group)
                                    dialog.dismiss()
                                }
                            }
                        }

                        override fun onFailure(call: Call<Group>, t: Throwable) {
                            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        }

        dialog.setView(view)
        dialog.show()
    }

    private fun showRemoveMemberConfirmDialog(groupId: Long, user: User) {
        AlertDialog.Builder(context)
            .setTitle("移除成员")
            .setMessage("确定要将 ${user.username} 移出群聊吗？")
            .setPositiveButton("确定") { _, _ ->
                ApiClient.service.removeGroupMember(groupId, user.id)
                    .enqueue(object : Callback<Group> {
                        override fun onResponse(call: Call<Group>, response: Response<Group>) {
                            if (response.isSuccessful) {
                                Toast.makeText(context, "已移除成员", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "移除成员失败", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<Group>, t: Throwable) {
                            Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddMemberDialog(group: Group) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("添加群成员")
            .create()

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_member, null)
        val contactsList = view.findViewById<RecyclerView>(R.id.contactsList)

        ApiClient.service.getUserContacts(currentUserId)
            .enqueue(object : Callback<List<User>> {
                override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { contacts ->
                            val availableContacts = contacts.filter { contact ->
                                !group.members.any { it.id == contact.id }
                            }
                            
                            val adapter = ContactSelectionAdapter(
                                contacts = availableContacts
                            ) { selectedUser: User ->
                                ApiClient.service.addGroupMember(group.id, selectedUser.id)
                                    .enqueue(object : Callback<Group> {
                                        override fun onResponse(call: Call<Group>, response: Response<Group>) {
                                            if (response.isSuccessful) {
                                                Toast.makeText(context, "已添加成员", Toast.LENGTH_SHORT).show()
                                                dialog.dismiss()
                                            } else {
                                                Toast.makeText(context, "添加成员失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        override fun onFailure(call: Call<Group>, t: Throwable) {
                                            Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                            }

                            contactsList.apply {
                                layoutManager = LinearLayoutManager(context)
                                this.adapter = adapter
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<List<User>>, t: Throwable) {
                    Toast.makeText(context, "加载联系人失败", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            })

        dialog.setView(view)
        dialog.show()
    }
} 