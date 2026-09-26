package com.husarp.browsertwins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlElement
import java.io.ByteArrayOutputStream
import java.io.File

// The clone engine, on the phone, no root. Proven on the PC (see TODO.md) with the same library calls:
//   1. read the source app's APK(s)                    - Apps.apkPaths
//   2. rename the package in the binary manifest+arsc  - ApkModule.setPackageName (ARSCLib)
//   3. rename app-defined permissions/authorities too  - fixManifest, below
//   4. set the clone's visible name                    - AndroidManifestBlock.setApplicationLabel
//   5. re-sign every part with our own key             - apksig
//   6. install all parts together                      - Installer (PackageInstaller)
object Cloner {

    class Failed(message: String) : Exception(message)

    // Builds the renamed, signed APK parts for a clone and returns the files (base first). Does NOT
    // install - the caller hands them to Installer. Runs off the main thread.
    fun build(ctx: Context, source: AppInfo, clonePkg: String, label: String,
              hue: Int = 0, strength: Int = 100, brightness: Int = 100): List<File> {
        val apks = Apps.apkPaths(ctx, source.pkg).map { File(it) }
        if (apks.isEmpty()) throw Failed("Could not read ${source.label}'s APK.")

        val work = File(ctx.cacheDir, "clones/$clonePkg").apply { deleteRecursively(); mkdirs() }
        val signer = Keys(ctx).signer()
        val out = mutableListOf<File>()

        for (apk in apks) {
            val module = ApkModule.loadApkFile(apk)
            val oldPkg = module.packageName
            module.setPackageName(clonePkg)
            fixManifest(module.androidManifest, oldPkg, clonePkg)
            // The base module carries the app label and launcher icon. ARSCLib's isBaseModule is
            // unreliable for a standalone split APK, so detect the base by its manifest: the base has
            // no "split" attribute, the splits do.
            if (isBaseManifest(module.androidManifest)) {
                module.androidManifest.setApplicationLabel(label)
                if (hue != 0 || strength != 100 || brightness != 100) recolourIcon(module, hue, strength, brightness)
            }

            val renamed = File(work, "unsigned-${apk.name}")
            module.writeApk(renamed)
            module.close()

            val signed = File(work, apk.name)
            sign(renamed, signed, signer)
            renamed.delete()
            // base first, so the installer writes it first
            if (apk.name.startsWith("base")) out.add(0, signed) else out.add(signed)
        }
        return out
    }

    // Recolour the launcher icon inside the clone: every PNG under res/mipmap (where launcher icons
    // live) is decoded, recoloured like LinkPilot's icons, and written back. Vector-only icons have no
    // PNG to recolour, so they keep the original look (see TODO).
    private fun recolourIcon(module: ApkModule, hue: Int, strength: Int, brightness: Int) {
        for (src in module.listInputSources().toList()) {
            val name = src.name
            if (!name.startsWith("res/mipmap") || !name.endsWith(".png")) continue
            try {
                val bytes = src.openStream().use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                val out = ByteArrayOutputStream()
                Recolour.apply(bmp, hue, strength, brightness).compress(Bitmap.CompressFormat.PNG, 100, out)
                module.add(ByteInputSource(out.toByteArray(), name))   // replaces the entry of the same name
            } catch (_: Exception) {
                // leave that image as it is
            }
        }
    }

    private fun sign(input: File, output: File, signer: Keys.Signer) {
        val config = ApkSigner.SignerConfig.Builder("BrowserTwins", KeyConfig.Jca(signer.privateKey), listOf(signer.certificate)).build()
        ApkSigner.Builder(listOf(config))
            .setInputApk(input).setOutputApk(output)
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true)
            .build().sign()
    }

    // Rename package-scoped strings that setPackageName leaves alone: app-defined permissions,
    // provider authorities, task affinities, sharedUserId. Class names are never touched.
    private fun fixManifest(mani: AndroidManifestBlock, oldPkg: String, newPkg: String) {
        val root = mani.manifestElement

        // Pass 1: every permission this app DECLARES (any name, not only ones under the package) must
        // become clone-unique, or it clashes with the original (INSTALL_FAILED_DUPLICATE_PERMISSION).
        val permMap = HashMap<String, String>()
        val decl = root.recursiveElements()
        while (decl.hasNext()) {
            val e = decl.next() as ResXmlElement
            if (local(e.name) != "permission") continue
            val p = nameAttr(e)?.valueAsString
            if (!p.isNullOrEmpty()) permMap[p] = uniquePerm(p, oldPkg, newPkg)
        }

        fixElement(root, oldPkg, newPkg, permMap)   // <manifest> itself (sharedUserId lives here)
        val els = root.recursiveElements()
        while (els.hasNext()) fixElement(els.next() as ResXmlElement, oldPkg, newPkg, permMap)
    }

    private fun fixElement(e: ResXmlElement, oldPkg: String, newPkg: String, permMap: Map<String, String>) {
        val tag = local(e.name)
        val permName = tag == "permission" || tag == "uses-permission" || tag == "uses-permission-sdk-23"
        val permTreeOrGroup = tag == "permission-group" || tag == "permission-tree"
        val attrs = e.attributes
        while (attrs.hasNext()) {
            val a = attrs.next() as ResXmlAttribute
            val an = local(a.name)
            val v = a.valueAsString ?: continue
            if (v.isEmpty()) continue
            val nv = when {
                an == "authorities" -> renameList(v, oldPkg, newPkg)
                an == "name" && permName -> permMap[v] ?: v
                an == "permission" || an == "readPermission" || an == "writePermission" -> permMap[v] ?: renameOne(v, oldPkg, newPkg)
                an == "taskAffinity" || an == "process" || an == "targetPackage" || an == "sharedUserId" ||
                    (an == "name" && permTreeOrGroup) -> renameOne(v, oldPkg, newPkg)
                else -> v
            }
            if (nv != v) a.valueAsString = nv
        }
    }

    // A clone-unique permission name: keep package-prefixed ones tidy, prefix the rest with the clone
    // package so two installed apps never declare the same permission name.
    private fun uniquePerm(p: String, oldPkg: String, newPkg: String) =
        if (p == oldPkg || p.startsWith("$oldPkg.")) renameOne(p, oldPkg, newPkg) else "$newPkg.$p"

    // The base APK's manifest has no "split" attribute; feature/config splits do.
    private fun isBaseManifest(mani: AndroidManifestBlock): Boolean {
        val attrs = mani.manifestElement.attributes
        while (attrs.hasNext()) if (local((attrs.next() as ResXmlAttribute).name) == "split") return false
        return true
    }

    private fun nameAttr(e: ResXmlElement): ResXmlAttribute? {
        val attrs = e.attributes
        while (attrs.hasNext()) {
            val a = attrs.next() as ResXmlAttribute
            if (local(a.name) == "name") return a
        }
        return null
    }

    private fun renameOne(v: String, oldPkg: String, newPkg: String) = when {
        v == oldPkg -> newPkg
        v.startsWith("$oldPkg.") -> newPkg + v.substring(oldPkg.length)
        else -> v
    }

    // Authorities may be a "a; b" list with spaces; trim each token before matching the prefix.
    private fun renameList(v: String, oldPkg: String, newPkg: String) =
        v.split(";").joinToString(";") { renameOne(it.trim(), oldPkg, newPkg) }

    private fun local(name: String) = name.substringAfterLast(':')

    // A package name for a new clone of [sourcePkg]: the original plus .btN, N the first free number.
    fun clonePackageName(sourcePkg: String, existing: List<Profile>): String {
        val used = existing.filter { it.sourcePkg == sourcePkg }.map { it.clonePkg }.toSet()
        var n = 1
        while ("$sourcePkg.bt$n" in used) n++
        return "$sourcePkg.bt$n"
    }
}
