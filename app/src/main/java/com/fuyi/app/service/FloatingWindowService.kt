package com.fuyi.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.fuyi.app.MainActivity
import com.fuyi.app.R
import com.fuyi.app.engine.TranslationPipeline

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var chineseText: TextView? = null
    private var japaneseText: TextView? = null
    private var paused = false

    private val segmentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (paused || intent == null) return

            val japanese = intent.getStringExtra(EXTRA_JAPANESE) ?: ""
            val chinese = intent.getStringExtra(EXTRA_CHINESE) ?: ""
            val isPartial = intent.getBooleanExtra(EXTRA_IS_PARTIAL, false)

            if (chinese.isNotEmpty()) {
                japaneseText?.text = japanese
                chineseText?.text = chinese
                chineseText?.alpha = 1.0f
            } else if (isPartial && japanese.isNotEmpty()) {
                japaneseText?.text = japanese
                chineseText?.alpha = 0.4f
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID, "浮译悬浮窗", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "浮译悬浮窗服务" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        inflateFloatingView()

        registerReceiver(segmentReceiver, IntentFilter(ACTION_SEGMENT), RECEIVER_EXPORTED)
    }

    private fun inflateFloatingView() {
        val root = FrameLayout(this).apply {
            setPadding(16, 12, 16, 12)
            setBackgroundResource(R.drawable.floating_bar_bg)
            elevation = 8f
        }

        japaneseText = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(0x80FFFFFF.toInt())
            maxLines = 1
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        chineseText = TextView(this).apply {
            text = "浮译准备就绪"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val textContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(japaneseText)
            addView(chineseText)
        }

        root.addView(textContainer)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isDragging = true
                        layoutParams!!.x = initialX + dx
                        layoutParams!!.y = initialY + dy
                        windowManager?.updateViewLayout(root, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        paused = !paused
                        if (paused) {
                            japaneseText?.text = ""
                            chineseText?.text = "已暂停"
                            chineseText?.alpha = 0.6f
                        } else {
                            chineseText?.text = "..."
                            chineseText?.alpha = 1.0f
                        }
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = root

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        windowManager?.addView(floatingView, layoutParams)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (floatingView != null) windowManager?.removeView(floatingView)
        try { unregisterReceiver(segmentReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("浮译悬浮窗运行中")
            .setContentText("翻译结果实时更新")
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "fuyi_floating"
        const val NOTIFICATION_ID = 1002
        const val ACTION_SEGMENT = "com.fuyi.action.SEGMENT"
        const val EXTRA_JAPANESE = "japanese"
        const val EXTRA_CHINESE = "chinese"
        const val EXTRA_IS_PARTIAL = "isPartial"

        fun sendSegment(context: Context, segment: TranslationPipeline.Segment) {
            val intent = Intent(ACTION_SEGMENT).apply {
                putExtra(EXTRA_JAPANESE, segment.japanese)
                putExtra(EXTRA_CHINESE, segment.chinese)
                putExtra(EXTRA_IS_PARTIAL, segment.isPartial)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }

        fun show(context: Context) {
            context.startForegroundService(Intent(context, FloatingWindowService::class.java))
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }
}
