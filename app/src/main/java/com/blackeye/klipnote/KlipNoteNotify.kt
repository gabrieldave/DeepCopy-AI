package com.blackeye.klipnote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object KlipNoteNotify {

    const val CHANNEL_PROGRESS = "klipnote_progress"
    /** Canal nuevo: Android no deja subir la importancia de uno ya creado. */
    const val CHANNEL_DONE = "klipnote_listo_v2"
    private const val CHANNEL_DONE_LEGACY = "klipnote_done"

    const val NOTIF_PROGRESS_ID = 4401
    const val NOTIF_DONE_ID = 4402

    const val EXTRA_OPEN_RESULT = "open_result"

    private const val PREFS = "klipnote_notify_prefs"
    private const val KEY_NOTIFY_DONE = "notify_when_done"
    private const val KEY_ASKED_PERM = "asked_post_notifications"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // El canal viejo pudo quedar en LOW/sin sonido; lo borramos y usamos ID nuevo.
        runCatching { nm.deleteNotificationChannel(CHANNEL_DONE_LEGACY) }
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.notif_channel_progress),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_progress_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                context.getString(R.string.notif_channel_done),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_done_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setShowBadge(true)
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
        val title = text.ifBlank { context.getString(R.string.notif_working) }
        val detail = when {
            progressPercent != null ->
                context.getString(R.string.notif_progress_detail, progressPercent)
            else -> context.getString(R.string.notif_progress_indeterminate)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(detail)
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.cancel), cancel)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (progressPercent != null) {
            builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
        } else {
            builder.setProgress(100, 0, true)
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
        if (Build.VERSION.SDK_INT >= 23) {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(NOTIF_PROGRESS_ID)
        }
    }

    /**
     * Quita una 4401 huérfana. Tras DETACH, a veces `cancel()` no basta:
     * hay que volver a asociarla a un FGS y hacer STOP_FOREGROUND_REMOVE.
     */
    fun clearOrphanProgressIfNeeded(context: Context) {
        if (KlipNoteJobHub.isBusy()) return
        clearProgress(context)
        val nm = if (Build.VERSION.SDK_INT >= 23) {
            context.getSystemService(NotificationManager::class.java)
        } else null
        val stuck = nm?.activeNotifications?.any { it.id == NOTIF_PROGRESS_ID } == true
        // Una sola vez por proceso: limpia fantasmas DETACH al abrir la app.
        if (stuck || !orphanSweepDone) {
            orphanSweepDone = true
            if (stuck) {
                KlipNoteWorkService.dismissOrphanProgress(context)
            }
        }
    }

    @Volatile
    private var orphanSweepDone: Boolean = false

    fun showFinished(
        context: Context,
        title: String,
        body: String,
        success: Boolean
    ) {
        // Siempre quita la de progreso; el aviso "Listo" solo si el usuario lo pidió.
        clearProgress(context)
        if (!isNotifyEnabled(context) || !canPostNotifications(context)) return
        ensureChannels(context)
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
            .setOnlyAlertOnce(false)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_DONE_ID, notif)
    }

    private fun pendingOpenApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_OPEN_RESULT, true)
        }
        return PendingIntent.getActivity(context, 3, intent, pendingFlags())
    }

    private fun pendingFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return flags
    }
}
