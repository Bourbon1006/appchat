package com.example.appchat.activity

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.appchat.R

class VideoPreviewActivity : AppCompatActivity() {
    private lateinit var videoView: VideoView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_preview)
        
        videoView = findViewById(R.id.videoView)
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        
        val videoUrl = intent.getStringExtra("videoUrl")
        if (videoUrl != null) {
            println("⭐ Playing video from URL: $videoUrl")
            try {
                videoView.setVideoURI(Uri.parse(videoUrl))
                videoView.setOnPreparedListener { mediaPlayer ->
                    println("✅ Video prepared successfully")
                    mediaPlayer.start()
                }
                videoView.setOnErrorListener { mp, what, extra ->
                    println("❌ Video playback error: what=$what, extra=$extra")
                    Toast.makeText(this, "视频播放失败", Toast.LENGTH_SHORT).show()
                    true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error setting video URI: ${e.message}")
                Toast.makeText(this, "无法加载视频", Toast.LENGTH_SHORT).show()
            }
        } else {
            println("❌ No video URL provided")
            Toast.makeText(this, "视频地址无效", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        findViewById<VideoView>(R.id.videoView).pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        findViewById<VideoView>(R.id.videoView).stopPlayback()
    }
} 