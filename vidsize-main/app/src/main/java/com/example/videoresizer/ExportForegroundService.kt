package com.example.videoresizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Exists purely to keep this app's process alive at foreground priority
 * while a video export is running — it does NOT run the export itself.
 *
 * WHY THIS EXISTS: [VideoResizer.resize] drives a [androidx.media3.transformer.Transformer]
 * entirely in-process, on whatever thread started it (Main, in this app's
 * case). None of that work was ever tied to a Service, so nothing was
 * stopping Android from killing the whole app process the moment it went
 * to the background — user swipes to another app, locks the screen,
 * anything — while a long export was still mid-flight. A "user awam"
 * naturally expects to be able to switch apps or lock their phone while
 * something processes in the background; losing the export silently
 * because of that is a real, high-impact bug for any video longer than a
 * few seconds.
 *
 * This service only:
 *  1. Calls [startForeground] so the OS treats this process as
 *     foreground-priority instead of an easily-killed background one.
 *  2. Shows a determinate progress notification, updated via
 *     [updateProgress], so there's an OS-level signal the export is still
 *     alive even after the user has switched away from the app entirely.
 *
 * The actual export logic is untouched — it still lives in
 * `runResize`/`VideoResizer.resize()`, called the same way it always was.
 * [start]/[updateProgress]/[stop] below are the only three entry points
 * the rest of the app needs to know about.
 *
 * Caveat that can't be engineered around: if Android kills the process
 * anyway under extreme memory pressure, the export dies with it — no app
 * can override the OS's low-memory killer. This closes off the *common*
 * background-kill case, not every conceivable one.
 */
class ExportForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        // HARDENING: startForeground() is allowed to throw
        // ForegroundServiceStartNotAllowedException on Android 12+ when the
        // OS decides the app doesn't currently qualify to be promoted to
        // foreground priority. That exception was previously unguarded,
        // which killed the whole app process — turning "export loses its
        // background-kill protection" (an acceptable degradation covered by
        // the class-level caveat above) into "export crashes outright"
        // (not acceptable). If the OS refuses, this service has nothing
        // left to do, so it stops itself; VideoResizer.resize() and its
        // Transformer keep running exactly as before, just without the
        // foreground-priority safety net for this one export.
        try {
            startForeground(NOTIFICATION_ID, buildNotification(this, progress = 0))
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_NOT_STICKY: if the OS kills this service anyway, the export
        // it was protecting dies with it (Transformer isn't Service-owned)
        // — restarting the service alone would just leave a phantom
        // "still processing" notification with nothing actually running
        // behind it, which is worse than no notification at all.
        return START_NOT_STICKY
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Proses video",
                    // LOW: this is a progress indicator, not an alert — no
                    // sound, no heads-up popup interrupting the user.
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Menunjukkan progres saat video sedang diproses"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "export_progress"
        private const val NOTIFICATION_ID = 4201

        /** Starts the service; safe to call even if it's already running. */
        fun start(context: Context) {
            val intent = Intent(context, ExportForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Updates the progress notification. Safe to call even without the
         * POST_NOTIFICATIONS permission (API 33+) — it just silently
         * won't be visible; the foreground-priority protection itself
         * doesn't depend on the notification being shown.
         */
        fun updateProgress(context: Context, percent: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            runCatching {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildNotification(context, percent))
            }
        }

        /** Stops the service and clears its notification. Safe to call even if it isn't running. */
        fun stop(context: Context) {
            context.stopService(Intent(context, ExportForegroundService::class.java))
        }

        private fun buildNotification(context: Context, progress: Int): Notification {
            val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val contentIntent = openAppIntent?.let {
                PendingIntent.getActivity(
                    context, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Memproses video…")
                .setContentText("$progress%")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .build()
        }
    }
}
