package com.husarp.browsertwins

import android.content.Context
import java.io.File

// The clone engine - the hard core, NOT finished yet (see TODO.md and PLAN.md).
//
// The plan, on-device and without root, is:
//   1. Read the source app's APK(s)          - DONE (Apps.apkPaths).
//   2. Rewrite the package name inside the    - NOT DONE. Needs rewriting the binary
//      binary AndroidManifest.xml and           AndroidManifest.xml and resources.arsc (the
//      resources.arsc.                          apktool approach), which is the real work.
//   3. Re-sign with our own key.              - NOT DONE. Needs a bundled ApkSigner + our key,
//                                                the same key every time so updates keep data.
//   4. Install via PackageInstaller.          - NOT DONE.
//
// Until step 2 exists there is nothing to install, so clone() copies the APK to prove step 1 works
// and then reports honestly. This keeps the UI wired to a real call instead of a fake success.
object Cloner {

    class NotFinished(message: String) : Exception(message)

    // Would build and install a clone of [source] as [clonePkg]. For now: copies the base APK into
    // the app's cache and throws NotFinished, so the caller shows the truth.
    fun clone(ctx: Context, source: AppInfo, clonePkg: String): Nothing {
        val apks = Apps.apkPaths(ctx, source.pkg)
        if (apks.isEmpty()) throw NotFinished("Could not read ${source.label}'s APK.")

        val dir = File(ctx.cacheDir, "clones/$clonePkg").apply { mkdirs() }
        try {
            File(apks[0]).copyTo(File(dir, "base.apk"), overwrite = true)
        } catch (e: Exception) {
            throw NotFinished("Could not copy the APK: ${e.message}")
        }

        val splits = if (apks.size > 1) " (+${apks.size - 1} split APKs to handle)" else ""
        throw NotFinished(
            "Copied ${source.label}'s APK$splits, but the clone engine isn't built yet: " +
            "rewriting the package name and re-signing the APK is the next task."
        )
    }

    // A package name for a new clone of [sourcePkg]: the original plus .btN, N being the first free
    // number. E.g. org.mozilla.firefox -> org.mozilla.firefox.bt1.
    fun clonePackageName(sourcePkg: String, existing: List<Profile>): String {
        val used = existing.filter { it.sourcePkg == sourcePkg }.map { it.clonePkg }.toSet()
        var n = 1
        while ("$sourcePkg.bt$n" in used) n++
        return "$sourcePkg.bt$n"
    }
}
