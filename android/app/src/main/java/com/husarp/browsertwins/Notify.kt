package com.husarp.browsertwins

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

// Small notifications for auto-update: "X updated", or "tap to finish updating X" on older Android.
object Notify {
    private const val CHANNEL = "updates"

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun show(ctx: Context, id: Int, title: String, text: String, tap: PendingIntent? = null) {
        ensureChannel(ctx)
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .apply { tap?.let { setContentIntent(it) } }
            .build()
        try { androidx.core.app.NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {
            // no notification permission - fine, the update itself still happened
        }
    }

    // An intent to open the OS install/confirm screen (used when a background update needs a tap).
    fun confirmIntent(ctx: Context, confirm: Intent): PendingIntent {
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, confirm.hashCode(), confirm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
}
