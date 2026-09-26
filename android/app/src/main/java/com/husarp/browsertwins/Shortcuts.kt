package com.husarp.browsertwins

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap

// The long-press menu on the Browser Twins icon: each profile that is switched on in the Menu tab
// becomes a shortcut that opens its clone. Refreshed on every Model.changed().
object Shortcuts {

    fun sync(ctx: Context, store: Store) {
        val pm = ctx.packageManager
        // Launchers show only a few; keep the first N that are on and still installed.
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx).coerceIn(4, 10)
        val shown = store.profiles.filter { it.inMenu && Apps.isInstalled(ctx, it.clonePkg) }.take(max)

        val list = shown.mapNotNull { p ->
            val launch = pm.getLaunchIntentForPackage(p.clonePkg) ?: return@mapNotNull null
            launch.action = Intent.ACTION_MAIN
            val builder = ShortcutInfoCompat.Builder(ctx, p.clonePkg)
                .setShortLabel(p.name)
                .setLongLabel(p.name)
                .setIntent(launch)
            icon(ctx, p)?.let { builder.setIcon(it) }
            builder.build()
        }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(ctx, list)
        } catch (_: Exception) {
            // some launchers/OEMs limit this; the app still works, just without the menu
        }
    }

    private fun icon(ctx: Context, p: Profile): IconCompat? {
        val bmp = Apps.icon(ctx, p.sourcePkg)?.toBitmap(192, 192) ?: return null
        val re = Recolour.apply(bmp, p.hue, p.strength, p.brightness)
        return IconCompat.createWithBitmap(re)
    }
}
