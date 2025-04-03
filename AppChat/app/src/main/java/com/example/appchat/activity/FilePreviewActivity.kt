package com.example.appchat.activity

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.appchat.R
import com.example.appchat.databinding.ActivityFilePreviewBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

class FilePreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFilePreviewBinding
    private lateinit var progressBar: ProgressBar
    private var downloadId: Long = -1
    private var fileUrl: String? = null
    private var fileName: String? = null
    private var fileType: String? = null

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                Log.d("FilePreviewActivity", "Download complete for ID: $downloadId")
                progressBar.visibility = View.GONE
                
                try {
                    // 获取下载的文件
                    val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (columnIndex != -1 && cursor.getInt(columnIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                            val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            if (localUriIndex != -1) {
                                val localUri = cursor.getString(localUriIndex)
                                Log.d("FilePreviewActivity", "Downloaded file URI: $localUri")
                                
                                // 从URI获取文件路径
                                val filePath = if (localUri.startsWith("file://")) {
                                    localUri.substring(7)
                                } else {
                                    Uri.parse(localUri).path
                                }
                                
                                if (filePath != null) {
                                    val file = File(filePath)
                                    Log.d("FilePreviewActivity", "File path: ${file.absolutePath}")
                                    
                                    if (file.exists()) {
                                        Log.d("FilePreviewActivity", "File exists, size: ${file.length()}")
                                        openFile(file)
                                    } else {
                                        Log.e("FilePreviewActivity", "File does not exist at path: ${file.absolutePath}")
                                        // 尝试使用预期的下载位置
                                        val expectedFile = File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            fileName ?: "downloaded_file"
                                        )
                                        if (expectedFile.exists()) {
                                            Log.d("FilePreviewActivity", "Found file at expected location: ${expectedFile.absolutePath}")
                                            openFile(expectedFile)
                                        } else {
                                            Log.e("FilePreviewActivity", "File not found at expected location either")
                                            Toast.makeText(context, "文件下载成功但无法找到", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Log.e("FilePreviewActivity", "Could not extract file path from URI: $localUri")
                                    Toast.makeText(context, "无法获取文件路径", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Log.e("FilePreviewActivity", "LOCAL_URI column not found")
                                Toast.makeText(context, "无法获取下载文件信息", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIndex != -1) cursor.getInt(reasonIndex) else -1
                            Log.e("FilePreviewActivity", "Download failed with reason code: $reason")
                            Toast.makeText(context, "文件下载失败，错误代码: $reason", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("FilePreviewActivity", "Cursor is empty, download info not found")
                        Toast.makeText(context, "找不到下载信息", Toast.LENGTH_SHORT).show()
                    }
                    cursor.close()
                } catch (e: Exception) {
                    Log.e("FilePreviewActivity", "Error processing download completion: ${e.message}", e)
                    Toast.makeText(context, "处理下载完成时出错: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "文件预览"
        
        progressBar = binding.progressBar
        
        // 获取传递的文件URL
        fileUrl = intent.getStringExtra("fileUrl")
        fileName = intent.getStringExtra("fileName") ?: fileUrl?.substringAfterLast("/")
        fileType = intent.getStringExtra("fileType") ?: fileName?.substringAfterLast(".")?.lowercase()
        
        Log.d("FilePreviewActivity", "Received file: $fileName, type: $fileType, URL: $fileUrl")
        
        if (fileUrl == null) {
            Toast.makeText(this, "无效的文件链接", Toast.LENGTH_SHORT).show()
            Log.e("FilePreviewActivity", "Invalid file URL")
            finish()
            return
        }
        
        // 设置文件名和类型信息
        binding.fileName.text = fileName
        binding.fileType.text = when (fileType) {
            "pdf" -> "PDF 文档"
            "doc", "docx" -> "Word 文档"
            "xls", "xlsx" -> "Excel 表格"
            "ppt", "pptx" -> "PowerPoint 演示文稿"
            "txt" -> "文本文件"
            else -> "未知文件类型"
        }
        
        // 设置文件图标
        when (fileType) {
            "pdf" -> binding.fileIcon.setImageResource(R.drawable.ic_pdf)
            "doc", "docx" -> binding.fileIcon.setImageResource(R.drawable.ic_word)
            "xls", "xlsx" -> binding.fileIcon.setImageResource(R.drawable.ic_excel)
            "ppt", "pptx" -> binding.fileIcon.setImageResource(R.drawable.ic_ppt)
            else -> binding.fileIcon.setImageResource(R.drawable.ic_file)
        }
        
        // 注册下载完成的广播接收器 - 添加导出标志
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 及以上需要指定导出属性
            registerReceiver(
                onDownloadComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            // 旧版本Android使用旧的API
            registerReceiver(
                onDownloadComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
        
        // 开始下载文件
        downloadFile()
        
        // 设置打开按钮点击事件
        binding.openButton.setOnClickListener {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName ?: "downloaded_file"
            )
            
            if (file.exists()) {
                Log.d("FilePreviewActivity", "Opening file: ${file.absolutePath}")
                openFile(file)
            } else {
                Log.e("FilePreviewActivity", "File does not exist: ${file.absolutePath}")
                Toast.makeText(this, "文件尚未下载完成，请稍候", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadFile() {
        Log.d("FilePreviewActivity", "Starting download: $fileUrl")
        progressBar.visibility = View.VISIBLE
        
        try {
            // 确保文件名不为空
            if (fileName.isNullOrEmpty()) {
                fileName = fileUrl?.substringAfterLast("/") ?: "downloaded_file"
                Log.d("FilePreviewActivity", "Generated fileName: $fileName")
            }
            
            // 确保下载目录存在
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
                Log.d("FilePreviewActivity", "Created download directory: ${downloadDir.absolutePath}")
            }
            
            // 检查文件是否已经存在
            val file = File(downloadDir, fileName!!)
            if (file.exists()) {
                Log.d("FilePreviewActivity", "File already exists, opening directly: ${file.absolutePath}")
                progressBar.visibility = View.GONE
                openFile(file)
                return
            }
            
            val request = DownloadManager.Request(Uri.parse(fileUrl))
                .setTitle(fileName)
                .setDescription("正在下载文件...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            Log.d("FilePreviewActivity", "Download started with ID: $downloadId")
        } catch (e: Exception) {
            Log.e("FilePreviewActivity", "Error starting download: ${e.message}", e)
            Toast.makeText(this, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
        }
    }

    private fun openFile(file: File) {
        try {
            Log.d("FilePreviewActivity", "Attempting to open file: ${file.absolutePath}")
            Log.d("FilePreviewActivity", "File exists: ${file.exists()}, File size: ${file.length()}")
            
            val extension = fileType?.lowercase()
            val mimeType = getMimeType(extension)
            Log.d("FilePreviewActivity", "MIME type: $mimeType")
            
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // 检查是否有应用可以处理此文件类型
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Log.e("FilePreviewActivity", "No apps can handle this file type")
                // 显示提示并提供下载选项
                showNoAppDialog()
            }
        } catch (e: Exception) {
            Log.e("FilePreviewActivity", "Error opening file: ${e.message}", e)
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
            
            // 显示错误对话框
            AlertDialog.Builder(this)
                .setTitle("打开文件失败")
                .setMessage("无法打开文件: ${e.message}\n\n您想尝试使用其他应用打开吗?")
                .setPositiveButton("是") { _, _ ->
                    try {
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.provider",
                            file
                        )
                        
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        startActivity(Intent.createChooser(intent, "选择应用打开文件"))
                    } catch (ex: Exception) {
                        Toast.makeText(this, "无法找到合适的应用打开此文件", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .setNegativeButton("否") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun getMimeType(extension: String?): String {
        return when (extension?.lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "*/*"
        }
    }

    private fun showNoAppDialog() {
        // 获取下载的文件路径
        val downloadedFilePath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName ?: "downloaded_file"
        ).absolutePath
        
        AlertDialog.Builder(this)
            .setTitle("无法打开文件")
            .setMessage("您的设备上没有安装可以打开此类型文件的应用程序。您可以选择下载合适的应用程序，或者使用其他方式查看此文件。")
            .setPositiveButton("下载应用") { _, _ ->
                // 根据文件类型打开应用商店搜索相关应用
                val intent = Intent(Intent.ACTION_VIEW)
                when (fileType?.lowercase()) {
                    "xlsx", "xls" -> intent.data = Uri.parse("market://search?q=excel viewer")
                    "docx", "doc" -> intent.data = Uri.parse("market://search?q=word viewer")
                    "pptx", "ppt" -> intent.data = Uri.parse("market://search?q=powerpoint viewer")
                    "pdf" -> intent.data = Uri.parse("market://search?q=pdf viewer")
                    else -> intent.data = Uri.parse("market://search?q=file viewer")
                }
                
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // 如果没有应用商店，则打开浏览器
                    val webIntent = Intent(Intent.ACTION_VIEW, 
                        Uri.parse("https://play.google.com/store/search?q=file viewer"))
                    startActivity(webIntent)
                }
            }
            .setNeutralButton("使用系统选择器") { _, _ ->
                // 使用系统文件选择器
                val file = File(downloadedFilePath)
                if (file.exists()) {
                    try {
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.provider",
                            file
                        )
                        
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        startActivity(Intent.createChooser(intent, "选择应用打开文件"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "无法找到合适的应用打开此文件", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "文件不存在: $downloadedFilePath", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createPdfRendererView(file: File): View {
        // 创建一个包含 ImageView 和控制按钮的布局
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // 创建 ImageView 显示 PDF 页面
        val imageView = ImageView(this)
        imageView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        container.addView(imageView)
        
        // 创建控制按钮布局
        val controlsLayout = LinearLayout(this)
        controlsLayout.orientation = LinearLayout.HORIZONTAL
        controlsLayout.gravity = Gravity.CENTER
        controlsLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        // 上一页按钮
        val prevButton = Button(this)
        prevButton.text = "上一页"
        prevButton.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        
        // 页码显示
        val pageText = TextView(this)
        pageText.gravity = Gravity.CENTER
        pageText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        
        // 下一页按钮
        val nextButton = Button(this)
        nextButton.text = "下一页"
        nextButton.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        
        controlsLayout.addView(prevButton)
        controlsLayout.addView(pageText)
        controlsLayout.addView(nextButton)
        container.addView(controlsLayout)
        
        // 打开 PDF 文件
        val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(parcelFileDescriptor)
        val pageCount = renderer.pageCount
        var currentPage = 0
        
        // 更新页码显示
        fun updatePageText() {
            pageText.text = "${currentPage + 1} / $pageCount"
        }
        
        // 渲染当前页
        fun renderPage() {
            renderer.openPage(currentPage).use { page ->
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                imageView.setImageBitmap(bitmap)
                updatePageText()
            }
        }
        
        // 设置按钮点击事件
        prevButton.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage()
            }
        }
        
        nextButton.setOnClickListener {
            if (currentPage < pageCount - 1) {
                currentPage++
                renderPage()
            }
        }
        
        // 初始渲染第一页
        renderPage()
        
        // 当活动销毁时关闭渲染器
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.DESTROYED) {
                try {
                    renderer.close()
                    parcelFileDescriptor.close()
                } catch (e: Exception) {
                    Log.e("FilePreviewActivity", "Error closing PDF renderer: ${e.message}", e)
                }
            }
        }
        
        return container
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(onDownloadComplete)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
} 