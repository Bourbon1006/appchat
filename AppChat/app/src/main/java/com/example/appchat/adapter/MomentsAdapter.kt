package com.example.appchat.adapter

import CommentAdapter
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.example.appchat.R
import com.example.appchat.databinding.ItemMomentBinding
import com.example.appchat.model.Moment
import java.time.format.DateTimeFormatter

class MomentsAdapter(
    private val onLikeClick: (Moment) -> Unit,
    private val onCommentClick: (Moment) -> Unit,
    private val onDeleteClick: (Moment) -> Unit,
    private val currentUserId: Long
) : RecyclerView.Adapter<MomentsAdapter.ViewHolder>() {

    private var moments = mutableListOf<Moment>()

    inner class ViewHolder(binding: ItemMomentBinding) : RecyclerView.ViewHolder(binding.root) {
        val ivAvatar: ImageView = binding.ivAvatar
        val tvUsername: TextView = binding.tvUsername
        val tvTimestamp: TextView = binding.tvTimestamp
        val tvContent: TextView = binding.tvContent
        val ivImage: ImageView = binding.ivImage
        val ivLike: ImageView = binding.ivLike
        val tvLikeCount: TextView = binding.tvLikeCount
        val tvCommentCount: TextView = binding.tvCommentCount
        val likeContainer: LinearLayout = binding.likeContainer
        val commentContainer: LinearLayout = binding.commentContainer
        val ivDelete: ImageView = binding.ivDelete
        val rvComments: RecyclerView = binding.rvComments
        val likesContainer: LinearLayout = binding.likesContainer
        val tvLikeUsers: TextView = binding.tvLikeUsers
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMomentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val moment = moments[position].copy()
        val context = holder.itemView.context
        val baseUrl = context.getString(R.string.server_url_format).format(
            context.getString(R.string.server_ip),
            context.getString(R.string.server_port)
        )
        
        // 加载用户头像
        Glide.with(holder.itemView.context)
            .load(baseUrl + moment.userAvatar)
            .placeholder(R.drawable.default_avatar)
            .error(R.drawable.default_avatar)
            .into(holder.ivAvatar)

        holder.tvUsername.text = moment.username
        holder.tvTimestamp.text = moment.createTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        holder.tvContent.text = moment.content
        
        // 加载动态图片
        if (moment.imageUrl != null) {
            holder.ivImage.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(baseUrl + moment.imageUrl)
                .override(Target.SIZE_ORIGINAL)
                .fitCenter()
                .error(R.drawable.ic_image_error)
                .into(holder.ivImage)
        } else {
            holder.ivImage.visibility = View.GONE
        }

        // 设置点赞数和评论数
        holder.tvLikeCount.text = moment.likeCount.toString()
        holder.tvCommentCount.text = moment.commentCount.toString()
        
        // 设置点赞状态
        holder.ivLike.setImageResource(
            if (moment.isLiked) R.drawable.ic_like_filled
            else R.drawable.ic_like
        )

        // 设置删除按钮的可见性和点击事件
        if (moment.userId == currentUserId) {
            holder.ivDelete.visibility = View.VISIBLE
            holder.ivDelete.setOnClickListener {
                showDeleteConfirmDialog(holder.itemView.context, moment)
            }
        } else {
            holder.ivDelete.visibility = View.GONE
        }

        // 点击事件
        holder.likeContainer.setOnClickListener { 
            val updatedMoment = moment.copy(
                isLiked = !moment.isLiked,
                likeCount = if (!moment.isLiked) moment.likeCount + 1 else moment.likeCount - 1
            )
            moments[position] = updatedMoment
            notifyItemChanged(position)
            
            // 然后通知外部处理点赞请求
            onLikeClick(updatedMoment)
        }
        holder.commentContainer.setOnClickListener { onCommentClick(moment) }

        // 设置评论列表
        val commentAdapter = CommentAdapter()
        holder.rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
            isNestedScrollingEnabled = false  // 防止嵌套滚动问题
        }
        commentAdapter.updateComments(moment.comments)

        // 显示点赞用户列表
        if (!moment.likeUsers.isNullOrEmpty()) {
            holder.likesContainer.visibility = View.VISIBLE
            val likeUsersText = moment.likeUsers.joinToString(", ") { it.username }
            holder.tvLikeUsers.text = likeUsersText
        } else {
            holder.likesContainer.visibility = View.GONE
        }
    }

    override fun getItemCount() = moments.size

    fun updateMoments(newMoments: List<Moment>) {
        moments = newMoments.toMutableList()
        notifyDataSetChanged()
    }

    private fun showDeleteConfirmDialog(context: Context, moment: Moment) {
        AlertDialog.Builder(context)
            .setTitle("删除动态")
            .setMessage("确定要删除这条动态吗？")
            .setPositiveButton("确定") { _, _ ->
                onDeleteClick(moment)
            }
            .setNegativeButton("取消", null)
            .show()
    }
} 