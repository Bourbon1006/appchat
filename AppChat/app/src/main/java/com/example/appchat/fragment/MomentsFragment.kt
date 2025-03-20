package com.example.appchat.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appchat.R
import com.example.appchat.adapter.MomentsAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.databinding.FragmentMomentsBinding
import com.example.appchat.model.Moment
import com.example.appchat.util.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.appchat.activity.CreateMomentActivity
import com.example.appchat.api.CreateCommentRequest

class MomentsFragment : Fragment() {

    private var _binding: FragmentMomentsBinding? = null
    private val binding get() = _binding!!
    private lateinit var momentsAdapter: MomentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMomentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        loadMoments()
    }

    private fun setupRecyclerView() {
        momentsAdapter = MomentsAdapter(
            onLikeClick = { moment ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (moment.isLiked) {
                                ApiClient.apiService.likeMoment(moment.id, UserPreferences.getUserId(requireContext()))
                            } else {
                                ApiClient.apiService.unlikeMoment(moment.id, UserPreferences.getUserId(requireContext()))
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            loadMoments()
                            Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onCommentClick = { moment ->
                showCommentDialog(moment)
            },
            onDeleteClick = { moment ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            ApiClient.apiService.deleteMoment(
                                momentId = moment.id,
                                userId = UserPreferences.getUserId(requireContext())
                            )
                        }
                        // 删除成功后刷新列表
                        loadMoments()
                        Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            currentUserId = UserPreferences.getUserId(requireContext())
        )
        binding.momentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = momentsAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadMoments()
        }
    }

    private fun setupFab() {
        binding.fabNewMoment.setOnClickListener {
            val intent = Intent(requireContext(), CreateMomentActivity::class.java)
            startActivityForResult(intent, CREATE_MOMENT_REQUEST)
        }
    }

    private fun loadMoments() {
        val userId = UserPreferences.getUserId(requireContext())
        binding.swipeRefresh.isRefreshing = true
        
        lifecycleScope.launch {
            try {
                val moments = ApiClient.apiService.getFriendMoments(userId)
                withContext(Dispatchers.Main) {
                    momentsAdapter.updateMoments(moments)
                    binding.swipeRefresh.isRefreshing = false
                    
                    // 显示空状态
                    if (moments.isEmpty()) {
                        binding.emptyView.visibility = View.VISIBLE
                        binding.momentsRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyView.visibility = View.GONE
                        binding.momentsRecyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    val errorMessage = when (e) {
                        is retrofit2.HttpException -> {
                            when (e.code()) {
                                404 -> "找不到动态数据"
                                500 -> "服务器错误"
                                else -> "加载失败: ${e.message()}"
                            }
                        }
                        else -> "网络错误: ${e.message}"
                    }
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                    
                    // 显示空状态
                    binding.emptyView.visibility = View.VISIBLE
                    binding.momentsRecyclerView.visibility = View.GONE
                }
            }
        }
    }

    private fun showCommentDialog(moment: Moment) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("发表评论")
            .setView(R.layout.dialog_comment)
            .create()

        dialog.show()

        val etComment = dialog.findViewById<EditText>(R.id.etComment)
        val btnSubmit = dialog.findViewById<Button>(R.id.btnSubmit)

        btnSubmit?.setOnClickListener {
            val content = etComment?.text.toString()
            if (content.isBlank()) {
                Toast.makeText(context, "请输入评论内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ApiClient.apiService.addComment(
                            momentId = moment.id,
                            userId = UserPreferences.getUserId(requireContext()),
                            request = CreateCommentRequest(content)
                        )
                    }
                    dialog.dismiss()
                    loadMoments()
                    Toast.makeText(context, "评论成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "评论失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_MOMENT_REQUEST && resultCode == Activity.RESULT_OK) {
            loadMoments()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CREATE_MOMENT_REQUEST = 1
    }
} 