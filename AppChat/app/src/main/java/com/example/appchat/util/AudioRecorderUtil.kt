package com.example.appchat.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorderUtil(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var startTime: Long = 0
    private var duration: Long = 0
    private var onErrorListener: ((String) -> Unit)? = null

    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }

    fun startRecording(): File? {
        if (isRecording) {
            Log.w("AudioRecorderUtil", "Already recording")
            return null
        }

        try {
            // 创建临时文件
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            if (storageDir == null) {
                onErrorListener?.invoke("无法创建录音文件：存储目录不可用")
                return null
            }

            audioFile = File.createTempFile(
                "AUDIO_${timeStamp}_",
                ".m4a",
                storageDir
            )

            // 初始化 MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                setOnErrorListener { _, what, extra ->
                    val error = "录音错误: $what, $extra"
                    Log.e("AudioRecorderUtil", error)
                    onErrorListener?.invoke(error)
                    stopRecording()
                }
                prepare()
                start()
            }

            isRecording = true
            startTime = System.currentTimeMillis()
            return audioFile
        } catch (e: IOException) {
            val error = "录音失败: ${e.message}"
            Log.e("AudioRecorderUtil", error, e)
            onErrorListener?.invoke(error)
            cleanup()
            return null
        } catch (e: IllegalStateException) {
            val error = "录音器状态错误: ${e.message}"
            Log.e("AudioRecorderUtil", error, e)
            onErrorListener?.invoke(error)
            cleanup()
            return null
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) {
            Log.w("AudioRecorderUtil", "Not recording")
            return null
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            duration = System.currentTimeMillis() - startTime

            // 检查文件是否存在且有效
            if (audioFile?.exists() == true && audioFile?.length() ?: 0 > 0) {
                return audioFile
            } else {
                onErrorListener?.invoke("录音文件无效")
                return null
            }
        } catch (e: Exception) {
            val error = "停止录音失败: ${e.message}"
            Log.e("AudioRecorderUtil", error, e)
            onErrorListener?.invoke(error)
            cleanup()
            return null
        }
    }

    fun getDuration(): Long {
        return duration
    }

    fun isRecording(): Boolean {
        return isRecording
    }

    fun cancelRecording() {
        if (isRecording) {
            cleanup()
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderUtil", "Error cleaning up MediaRecorder", e)
        }
        mediaRecorder = null
        isRecording = false
        
        // 删除临时文件
        audioFile?.delete()
        audioFile = null
    }
} 