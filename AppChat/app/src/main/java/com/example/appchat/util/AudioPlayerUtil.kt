package com.example.appchat.util

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AudioPlayerUtil(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var currentFile: File? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onProgressListener: ((Int) -> Unit)? = null
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun playAudio(file: File) {
        if (isPlaying) {
            stopAudio()
        }

        executor.execute {
            try {
                val player = MediaPlayer()
                player.setDataSource(file.absolutePath)
                player.prepareAsync()
                player.setOnPreparedListener { mp ->
                    mainHandler.post {
                        mp.start()
                        mediaPlayer = mp
                        isPlaying = true
                        currentFile = file
                    }
                }
                player.setOnCompletionListener {
                    mainHandler.post {
                        isPlaying = false
                        onCompletionListener?.invoke()
                    }
                }
                player.setOnErrorListener { _, what, extra ->
                    mainHandler.post {
                        isPlaying = false
                        onErrorListener?.invoke("播放错误: $what, $extra")
                    }
                    true
                }
            } catch (e: IOException) {
                mainHandler.post {
                    onErrorListener?.invoke("播放失败: ${e.message}")
                }
            }
        }
    }

    fun playAudioFromUrl(url: String) {
        try {
            // 停止当前播放
            stopAudio()

            // 在后台线程中准备和播放音频
            executor.execute {
                try {
                    // 创建新的MediaPlayer
                    val newMediaPlayer = MediaPlayer().apply {
                        setDataSource(url)
                        setOnPreparedListener { mp ->
                            mainHandler.post {
                                mp.start()
                                this@AudioPlayerUtil.isPlaying = true  // 设置播放状态为true
                                // 开始进度更新
                                startProgressUpdates()
                            }
                        }
                        
                        // 设置错误监听
                        setOnErrorListener { _, what, extra ->
                            mainHandler.post {
                                val error = "播放错误: $what, $extra"
                                onErrorListener?.invoke(error)
                                stopAudio()
                            }
                            true  // 返回true表示错误已处理
                        }
                        
                        // 设置完成监听
                        setOnCompletionListener {
                            mainHandler.post {
                                onCompletionListener?.invoke()
                                stopAudio()
                            }
                        }
                        
                        // 在后台线程中准备
                        prepareAsync()
                    }
                    mediaPlayer = newMediaPlayer
                } catch (e: Exception) {
                    mainHandler.post {
                        val error = "播放失败: ${e.message}"
                        onErrorListener?.invoke(error)
                        stopAudio()
                    }
                }
            }
        } catch (e: Exception) {
            val error = "播放失败: ${e.message}"
            onErrorListener?.invoke(error)
            stopAudio()
        }
    }

    fun stopAudio() {
        try {
            // 停止进度更新
            progressHandler?.removeCallbacks(progressRunnable!!)
            progressHandler = null
            progressRunnable = null

            // 停止播放
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            isPlaying = false
            currentFile = null
        } catch (e: Exception) {
            onErrorListener?.invoke("停止播放失败: ${e.message}")
        }
    }

    fun pauseAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    pause()
                    this@AudioPlayerUtil.isPlaying = false
                    // 停止进度更新
                    progressHandler?.removeCallbacks(progressRunnable!!)
                }
            }
        } catch (e: Exception) {
            onErrorListener?.invoke("暂停播放失败: ${e.message}")
        }
    }

    fun resumeAudio() {
        try {
            mediaPlayer?.apply {
                if (!isPlaying) {
                    start()
                    this@AudioPlayerUtil.isPlaying = true
                    // 重新开始进度更新
                    startProgressUpdates()
                }
            }
        } catch (e: Exception) {
            onErrorListener?.invoke("恢复播放失败: ${e.message}")
        }
    }

    fun isPlaying(): Boolean {
        return isPlaying
    }

    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }

    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }

    fun setOnProgressListener(listener: (Int) -> Unit) {
        onProgressListener = listener
    }

    fun getCurrentFile(): File? {
        return currentFile
    }

    fun release() {
        stopAudio()
        executor.shutdown()
    }

    private fun startProgressUpdates() {
        val newProgressHandler = Handler(Looper.getMainLooper())
        val newProgressRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val progress = (player.currentPosition * 100 / player.duration).toInt()
                        onProgressListener?.invoke(progress)
                        newProgressHandler.postDelayed(this, 100) // 每100ms更新一次进度
                    }
                }
            }
        }
        progressHandler = newProgressHandler
        progressRunnable = newProgressRunnable
        progressHandler?.post(progressRunnable!!)
    }
} 