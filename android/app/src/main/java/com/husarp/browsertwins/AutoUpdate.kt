package com.husarp.browsertwins

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// Keeps clones up to date by themselves. When "Update automatically" is on, a daily background check
// rebuilds any clone whose original app has a newer version and installs it over the clone (data kept;
// silent on Android 12+, a tap on older Android via InstallReceiver).
object AutoUpdate {
    private const val WORK = "auto-update"

    // Call when the setting changes and on app start, so the schedule matches the settings.
    fun apply(ctx: Context, store: Store) {
        val wm = WorkManager.getInstance(ctx)
        if (!store.autoUpdate) { wm.cancelUniqueWork(WORK); return }
        val constraints = Constraints.Builder()
            .apply { if (store.onlyWhenCharging) setRequiresCharging(true) }
            .build()
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        if (!store.autoUpdate) return Result.success()
        for (p in store.profiles) {
            if (!Apps.isInstalled(ctx, p.clonePkg) || !Apps.isInstalled(ctx, p.sourcePkg)) continue
            val v = Apps.version(ctx, p.sourcePkg)
            if (v.isEmpty() || v == p.madeFromVersion) continue
            try {
                val src = AppInfo(p.sourcePkg, Apps.label(ctx, p.sourcePkg), v, false)
                val parts = Cloner.build(ctx, src, p.clonePkg, p.name, p.hue, p.strength, p.brightness)
                Installer.commitSilent(ctx, parts, p.clonePkg, v)   // result handled by InstallReceiver
            } catch (e: Exception) {
                store.addLog("Auto-update failed: ${p.name}", e.message ?: "")
            }
        }
        return Result.success()
    }
}
