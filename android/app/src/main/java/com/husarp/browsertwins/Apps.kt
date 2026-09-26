package com.husarp.browsertwins

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

// One app on the phone that can be cloned.
data class AppInfo(val pkg: String, val label: String, val version: String, val isBrowser: Boolean)

// The apps on the phone (those with a launcher icon: browsers, Messenger...), and how to read one's
// APK for cloning. Uses the <queries> LAUNCHER filter in the manifest - no all-packages permission.
object Apps {

    // Browsers first, then the rest, each list sorted by name.
    fun all(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val browsers = browserPackages(ctx)
        val self = ctx.packageName
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0).mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == self) return@mapNotNull null
            AppInfo(pkg, label(ctx, pkg), version(ctx, pkg), pkg in browsers)
        }.distinctBy { it.pkg }
        return apps.sortedWith(compareByDescending<AppInfo> { it.isBrowser }.thenBy { it.label.lowercase() })
    }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    fun version(ctx: Context, pkg: String): String = try {
        ctx.packageManager.getPackageInfo(pkg, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    fun icon(ctx: Context, pkg: String): Drawable? = try {
        ctx.packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) { null }

    // Whether a package is installed (clones have a launcher, so they're visible to us).
    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: Exception) { false }

    // The APK files of an installed app. Modern apps ship several (a base APK plus splits); the clone
    // engine has to handle all of them. Returns the base first.
    fun apkPaths(ctx: Context, pkg: String): List<String> = try {
        val info = ctx.packageManager.getApplicationInfo(pkg, 0)
        val base = info.sourceDir
        val splits = info.splitSourceDirs?.toList() ?: emptyList()
        listOf(base) + splits
    } catch (_: Exception) { emptyList() }

    // The default-browser role holders, so we can mark browsers in the list.
    private fun browserPackages(ctx: Context): Set<String> {
        val view = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return ctx.packageManager.queryIntentActivities(view, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet()
    }
}
