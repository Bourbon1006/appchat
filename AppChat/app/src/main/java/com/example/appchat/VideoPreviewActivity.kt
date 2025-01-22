package com.example.appchat

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class VideoPreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_preview)

        val videoUrl = intent.getStringExtra("videoUrl")
        val videoView = findViewById<VideoView>(R.id.videoView)
        
        videoUrl?.let {
            val mediaController = MediaController(this)
            mediaController.setAnchorView(videoView)
            videoView.setMediaController(mediaController)
            videoView.setVideoURI(Uri.parse(it))
            videoView.requestFocus()
            videoView.start()
        }
    }
} 