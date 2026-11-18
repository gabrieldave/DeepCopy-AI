package com.blackeye.klipnote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object KlipNoteNotify {

    const val CHANNEL_PROGRESS = "klipnote_progress"
    const val CHANNEL_DONE = "klipnote_done"
    const val NOTIF_PROGRESS_ID = 4401
    const val NOTIF_DONE_ID = 4402

    const val EXTRA_OPEN_RESULT = "open_result"

    private const val PREFS = "klipnote_notify_prefs"
    private const val KEY_NOTIFY_DONE = "notify_when_done"
    private const val KEY_ASKED_PERM = "asked_post_notifications"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "Progreso de descarga",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el avance mientras KlipNote descarga o transcribe"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                "Avisos al terminar",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Te avisa cuando el guion o la descarga están listos"
                enableVibration(true)
            }
        )
    }

    fun isNotifyEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFY_DONE, true)

    fun setNotifyEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFY_DONE, enabled)
            .apply()
    }

    fun hasAskedNotificationPermission(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED_PERM, false)

    fun markAskedNotificationPermission(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ASKED_PERM, true)
            .apply()
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun progressNotification(
        context: Context,
        text: String,
        progressPercent: Int? = null
    ): Notification {
        ensureChannels(context)
        val open = pendingOpenApp(context)
        val cancel = PendingIntent.getService(
            context,
            2,
            Intent(context, KlipNoteWorkService::class.java).setAction(KlipNoteWorkService.ACTION_CANCEL),
            pendingFlags()
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("KlipNote")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "Cancelar", cancel)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (progressPercent != null) {
            builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun updateProgress(context: Context, text: String, progressPercent: Int? = null) {
        if (!canPostNotifications(context)) return
        NotificationManagerCompat.from(context)
            .notify(NOTIF_PROGRESS_ID, progressNotification(context, text, progressPercent))
    }

    fun clearProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_PROGRESS_ID)
    }

    fun showFinished(
        context: Context,
        title: String,
        body: String,
        success: Boolean
    ) {
        if (!isNotifyEnabled(context) || !canPostNotifications(context)) return
        ensureChannels(context)
        clearProgress(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingOpenApp(context))
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_DONE_ID, notif)
    }

    private fun pendingOpenApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_RESULT, true)
        }
        return PendingIntent.getActivity(context, 1, intent, pendingFlags())
    }

    private fun pendingFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return flags
    }
}
