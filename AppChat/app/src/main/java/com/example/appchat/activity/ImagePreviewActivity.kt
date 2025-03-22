package com.example.appchat.activity

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.appchat.R
import com.github.chrisbanes.photoview.PhotoView

class ImagePreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        val imageUrl = intent.getStringExtra("imageUrl")
        val photoView = findViewById<PhotoView>(R.id.photoView)
        
        imageUrl?.let {
            Glide.with(this)
                .load(it)
                .into(photoView)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 