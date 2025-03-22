package com.example.appchat.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appchat.R
import com.example.appchat.api.ApiClient
import com.example.appchat.model.Group
import com.example.appchat.util.FileUtil
import com.example.appchat.util.loadAvatar
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody

class GroupAdapter(
    private val onItemClick: (Group) -> Unit,
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context
) : RecyclerView.Adapter<GroupAdapter.ViewHolder>() {

    private var groups: List<Group> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val groupName: TextView = view.findViewById(R.id.groupName)
        val memberCount: TextView = view.findViewById(R.id.memberCount)
        val groupAvatar: ImageView = view.findViewById(R.id.groupAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        
        // 显示群组名称
        holder.groupName.text = group.name
        
        // 直接使用后端返回的memberCount字段
        holder.memberCount.text = "${group.memberCount} 成员"
        
        // 构建基础URL
        val baseUrl = holder.itemView.context.getString(
            R.string.server_url_format,
            holder.itemView.context.getString(R.string.server_ip),
            holder.itemView.context.getString(R.string.server_port)
        )
        
        // 加载群组头像
        val avatarUrl = "$baseUrl/api/groups/${group.id}/avatar"
        holder.groupAvatar.loadAvatar(avatarUrl)
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick.invoke(group)
        }
    }

    override fun getItemCount() = groups.size

    fun updateGroups(newGroups: List<Group>) {
        groups = newGroups
        notifyDataSetChanged()
    }

    private fun uploadGroupAvatar(groupId: Long, uri: Uri) {
        val file = FileUtil.getFileFromUri(context, uri) ?: run {
            Toast.makeText(context, "无法读取文件", Toast.LENGTH_SHORT).show()
            return
        }

        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("avatar", file.name, requestFile)

        lifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.uploadGroupAvatar(groupId, body)
                if (response.isSuccessful) {
                    notifyDataSetChanged()
                    Toast.makeText(context, "群头像上传成功", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = response.errorBody()?.string() ?: "未知错误"
                    Toast.makeText(context, "上传失败: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 