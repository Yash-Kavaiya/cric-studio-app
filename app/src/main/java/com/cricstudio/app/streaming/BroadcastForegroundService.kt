package com.cricstudio.app.streaming

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cricstudio.app.MainActivity
import com.cricstudio.app.R
import com.cricstudio.app.model.CompositorSnapshot
import com.cricstudio.app.model.StreamDestination
import com.cricstudio.app.model.StreamQuality
import com.cricstudio.app.model.StreamTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-scoped controller bridging ViewModel, CommentatorEngine, and BroadcastForegroundService.
 */
object BroadcastController {
    @Volatile
    private var engine: BroadcastEngine? = null
    private val fallbackTelemetry = MutableStateFlow(StreamTelemetry())

    @Synchronized
    fun ensureEngine(context: Context): BroadcastEngine {
        return engine ?: BroadcastEngine(context.applicationContext).also { engine = it }
    }

    fun telemetry(context: Context): StateFlow<StreamTelemetry> {
        return ensureEngine(context).telemetry
    }

    fun updateSnapshot(context: Context, snapshot: CompositorSnapshot) {
        ensureEngine(context).updateSnapshot(snapshot)
    }

    fun enqueueTtsPcm(samples: ShortArray) {
        engine?.enqueueTtsPcm(samples)
    }

    fun setMicEnabled(enabled: Boolean) {
        engine?.setMicEnabled(enabled)
    }

    fun startBroadcast(
        context: Context,
        destinations: List<StreamDestination>,
        quality: StreamQuality,
        hasMicPermission: Boolean,
        micEnabled: Boolean,
    ) {
        val eng = ensureEngine(context)
        eng.start(destinations, quality, hasMicPermission, micEnabled)
        val intent = Intent(context, BroadcastForegroundService::class.java).apply {
            action = BroadcastForegroundService.ACTION_START
            putExtra(BroadcastForegroundService.EXTRA_WITH_MIC, hasMicPermission && micEnabled)
        }
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun stopBroadcast(context: Context) {
        engine?.stop()
        val intent = Intent(context, BroadcastForegroundService::class.java).apply {
            action = BroadcastForegroundService.ACTION_STOP
        }
        runCatching { context.stopService(intent) }
    }
}

class BroadcastForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.cricstudio.app.action.START_BROADCAST"
        const val ACTION_STOP = "com.cricstudio.app.action.STOP_BROADCAST"
        const val EXTRA_WITH_MIC = "with_mic"
        private const val CHANNEL_ID = "cricstudio_live_broadcast"
        private const val NOTIF_ID = 4401
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BroadcastController.stopBroadcast(applicationContext)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        val notif = buildNotification()
        val withMic = intent?.getBooleanExtra(EXTRA_WITH_MIC, false) == true &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && withMic) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
            startForeground(NOTIF_ID, notif, fgsType)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        BroadcastController.ensureEngine(applicationContext).stop()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Live Cricket Broadcast",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Active RTMP broadcast to YouTube Live and other platforms"
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BroadcastForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CricStudio ON AIR")
            .setContentText("Streaming live cricket broadcast via RTMP")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "End Live", stopIntent)
            .build()
    }
}
