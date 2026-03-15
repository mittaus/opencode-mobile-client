package com.opencode.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service whose sole purpose is to elevate the process priority so that
 * Android does NOT apply Doze mode or battery optimization while a coding session is active.
 *
 * With the process at foreground priority the SSE connection inside [ChatViewModel]'s
 * viewModelScope can continue uninterrupted even when the screen locks.
 *
 * The service intentionally does NOT manage the SSE connection itself — the SSE must
 * live in [ChatViewModel] so it is created after the active project path is set,
 * ensuring it connects to the correct per-project OpenCode process port.
 *
 * Lifecycle:
 *  - Started by [ConnectViewModel] right after the Koin network module is loaded.
 *  - Stopped when the user taps "Detener" in the notification or sends ACTION_STOP.
 */
class OpenCodeSseService : Service() {

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Sesión activa"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "ACTION_STOP")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    // ── Notification ───────────────────────────────────────────────────────

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val openPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopPending = PendingIntent.getService(
            this, 1,
            Intent(this, OpenCodeSseService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("OpenCode Remote")
            .setContentText(contentText)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenCode sesión activa",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantiene la conexión al servidor activa en segundo plano"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "OC-SseService"
        const val CHANNEL_ID = "opencode_sse"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.opencode.ACTION_STOP_SSE"
    }
}
