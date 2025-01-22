package com.example.appchat.util

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

class CustomLongClickListener(
    private val delayMillis: Long = 500, // 默认500毫秒
    private val onLongClick: () -> Unit
) : View.OnTouchListener {
    private val handler = Handler(Looper.getMainLooper())
    private val longClickRunnable = Runnable { onLongClick() }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handler.postDelayed(longClickRunnable, delayMillis)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longClickRunnable)
            }
        }
        return true
    }
} 