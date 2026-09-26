package com.husarp.browsertwins

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import java.io.File

// Installs a clone's APK parts with PackageInstaller. Android shows its own "Install?" question the
// first time; a later update of an app we installed can be silent on Android 12+.
object Installer {

    private const val ACTION = "com.husarp.browsertwins.INSTALL_RESULT"

    // Background install for auto-update: no callback, no Activity. Silent on Android 12+ (no prompt);
    // on older Android the result comes to InstallReceiver, which posts a "tap to finish" notification.
    // The result carries the clone package and the version it was rebuilt from, so the receiver can
    // record the update.
    fun commitSilent(ctx: Context, parts: List<File>, clonePkg: String, newVersion: String) {
        val pm = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = pm.createSession(params)
        pm.openSession(sessionId).use { session ->
            for (part in parts) {
                session.openWrite(part.name, 0, part.length()).use { out ->
                    part.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallReceiver::class.java)
                .putExtra("clonePkg", clonePkg).putExtra("newVersion", newVersion)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(ctx, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    // Writes every part into one install session and commits it. onResult(success, message) fires when
    // Android reports back (after the user answers its "Install?" question).
    fun install(ctx: Context, parts: List<File>, onResult: (Boolean, String) -> Unit) {
        val pm = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = pm.createSession(params)
        val session = pm.openSession(sessionId)
        try {
            for (part in parts) {
                session.openWrite(part.name, 0, part.length()).use { out ->
                    part.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
            ctx.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.also { ctx.startActivity(it) }
                            return   // wait for the final result after the user answers
                        }
                        PackageInstaller.STATUS_SUCCESS -> { c.unregisterReceiver(this); onResult(true, "Installed") }
                        else -> {
                            c.unregisterReceiver(this)
                            onResult(false, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed")
                        }
                    }
                }
            }, IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED)

            val intent = Intent(ACTION).setPackage(ctx.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(ctx, sessionId, intent, flags)
            session.commit(pi.intentSender)
        } finally {
            session.close()
        }
    }
}
