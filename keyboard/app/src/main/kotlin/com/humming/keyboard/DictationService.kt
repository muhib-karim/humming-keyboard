package com.humming.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Keeps a dictation alive while the screen is off or the keyboard is hidden.
 *
 * Android stops giving microphone audio to an app that is not in the foreground (and may doze the CPU when the
 * screen turns off), which cut long dictations short. While the mic is on, this foreground service of type
 * "microphone" (with its ongoing notification and a Stop action) plus a partial wake lock keeps capture and the
 * on-device transcriber running until the user taps stop. Nothing here touches the network.
 */
class DictationService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            onStopRequested?.invoke()
            return START_NOT_STICKY
        }
        val n = notification(this)
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
        } catch (t: Throwable) {
            // Not allowed right now (e.g. the keyboard was not visible): dictation still runs, only screen-off is at risk.
            Log.w("HUMMING", "dictation service could not go foreground: ${t.javaClass.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "humming:dictation").apply {
                setReferenceCounted(false)
                acquire(WAKE_TIMEOUT_MS)
            }
        }
        running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { wake?.let { if (it.isHeld) it.release() } }
        wake = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "humming_dictation"
        const val NOTIFICATION_ID = 4711
        const val ACTION_STOP = "com.humming.keyboard.action.STOP_DICTATION"
        /** Safety net only: a wake lock is never held forever if something goes wrong (12 h). */
        const val WAKE_TIMEOUT_MS = 12L * 60 * 60 * 1000

        /** Set by the keyboard: what the notification's Stop action does. */
        @Volatile var onStopRequested: (() -> Unit)? = null
        @Volatile var running = false
            private set

        /** Mic on: go foreground. Returns false when Android refuses (dictation then runs as before). */
        fun begin(ctx: Context): Boolean = try {
            val i = Intent(ctx, DictationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
            true
        } catch (t: Throwable) {
            Log.w("HUMMING", "dictation service not started: ${t.javaClass.simpleName}")
            false
        }

        /** Mic off: drop the notification and the wake lock. */
        fun end(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, DictationService::class.java)) }
            running = false
        }

        private fun notification(ctx: Context): Notification {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Dictation in progress",
                    NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while Humming is listening, so dictation keeps going with the screen off."
                    setShowBadge(false)
                })
            }
            val stop = PendingIntent.getService(ctx, 1,
                Intent(ctx, DictationService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setColor(0xFFE5486B.toInt()) // Humming brand pink
                .setContentTitle("Humming is listening")
                .setContentText("Dictation keeps going with the screen off. Tap Stop to finish.")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, "Stop", stop)
                .build()
        }
    }
}
