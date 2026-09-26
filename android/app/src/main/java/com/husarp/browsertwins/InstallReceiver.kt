package com.husarp.browsertwins

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

// Result of a background (auto-update) install started by Installer.commitSilent.
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val clonePkg = intent.getStringExtra("clonePkg") ?: return
        val newVersion = intent.getStringExtra("newVersion") ?: ""
        val store = Store(ctx.applicationContext)
        val name = store.profiles.firstOrNull { it.clonePkg == clonePkg }?.name ?: clonePkg

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Older Android (or conditions unmet): the user must tap to finish. Notify with the
                // OS confirm screen behind it.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                val tap = confirm?.let { Notify.confirmIntent(ctx, it) }
                if (store.notify) Notify.show(ctx, clonePkg.hashCode(), "Update ready", "Tap to finish updating $name", tap)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                store.profiles = store.profiles.map { if (it.clonePkg == clonePkg) it.copy(madeFromVersion = newVersion) else it }
                store.addLog("$name updated to $newVersion (data kept)")
                if (store.notify) Notify.show(ctx, clonePkg.hashCode(), "Updated", "$name updated to $newVersion")
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "failed"
                store.addLog("Couldn't update $name", msg)
            }
        }
    }
}
