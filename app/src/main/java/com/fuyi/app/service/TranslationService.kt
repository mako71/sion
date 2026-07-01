package com.fuyi.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fuyi.app.MainActivity
import com.fuyi.app.R
import com.fuyi.app.engine.AudioCaptureEngine
import com.fuyi.app.engine.JaZhTranslator
import com.fuyi.app.engine.TranslationPipeline
import com.fuyi.app.engine.VoskRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TranslationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pipeline: TranslationPipeline? = null
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID, "浮译音频捕获", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "浮译音频捕获服务的通知渠道" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (resultCode != -1 && data != null) {
                    startForeground(NOTIFICATION_ID, buildNotification("正在准备翻译引擎…"))
                    setupProjection(resultCode, data)
                }
            }
            ACTION_STOP -> stopEverything()
        }
        return START_STICKY
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopEverything() }
        }, null)

        scope.launch {
            val modelPath = VoskRecognizer.resolveModelPath(this@TranslationService)
            if (modelPath == null) {
                updateNotification("Vosk 日语模型未找到，请将模型放到 /sdcard/fuyi/model/")
                return@launch
            }

            val recognizer = VoskRecognizer(modelPath)
            val translator = JaZhTranslator()
            pipeline = TranslationPipeline(recognizer, translator)

            val ready = pipeline!!.prepare()
            if (!ready) {
                updateNotification("模型初始化失败")
                return@launch
            }

            updateNotification("浮译运行中 — 日语→中文")

            val audioFlow = AudioCaptureEngine.capture(mediaProjection!!)
            pipeline!!.start(audioFlow)

            pipeline!!.segments.onEach { segment ->
                FloatingWindowService.sendSegment(this@TranslationService, segment)
            }.launchIn(scope)
        }
    }

    private fun stopEverything() {
        pipeline?.stop()
        mediaProjection?.stop()
        mediaProjection = null
        pipeline = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TranslationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("浮译")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.fuyi.action.START"
        const val ACTION_STOP = "com.fuyi.action.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val CHANNEL_ID = "fuyi_audio_capture"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, TranslationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TranslationService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
