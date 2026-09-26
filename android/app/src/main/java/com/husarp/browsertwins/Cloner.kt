package com.husarp.browsertwins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import com.reandroid.apk.ApkModule
import com.reandroid.apk.ResFile
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.value.ValueType
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
        val recolour = hue != 0 || strength != 100 || brightness != 100
        // The launcher icon's resources, found from the base (always first in apkPaths) and then also
        // recoloured in the splits - a density split (split_config.xhdpi.apk) often holds the very
        // image the launcher shows.
        val iconIds = HashSet<Int>()

        for (apk in apks) {
            val module = ApkModule.loadApkFile(apk)
            val oldPkg = module.packageName
            module.setPackageName(clonePkg)
            fixManifest(module.androidManifest, oldPkg, clonePkg)
            // The base module carries the app label and the icon reference. ARSCLib's isBaseModule is
            // unreliable for a standalone split APK, so detect the base by its manifest: the base has
            // no "split" attribute, the splits do.
            if (isBaseManifest(module.androidManifest)) {
                val mani = module.androidManifest
                mani.setApplicationLabel(label)
                if (recolour) iconIds += listOf(mani.iconResourceId, mani.roundIconResourceId).filter(::isAppRes)
            }
            if (recolour && iconIds.isNotEmpty()) recolourIcon(module, iconIds, hue, strength, brightness)

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

    // Recolour the launcher icon inside the clone by following its resources, whatever the files are
    // called (apps often obfuscate them, e.g. Brave's foreground is res/PQv, a WebP):
    //   manifest icon -> adaptive-icon / layer-list XML -> its drawable references -> ...
    // Raster files (PNG, WebP, JPEG) are recoloured like LinkPilot's icons; vector drawables get every
    // colour value (fill, stroke, gradient, tint) moved through the same matrix. Framework resources
    // (e.g. a system white background) are left alone. [ids] grows as references are found, so the
    // splits recolour the same resources the base pointed to.
    private fun recolourIcon(module: ApkModule, ids: MutableSet<Int>, hue: Int, strength: Int, brightness: Int) {
        val byId = HashMap<Int, MutableList<ResFile>>()
        for (rf in module.listResFiles()) for (e in rf.entryList) byId.getOrPut(e.resourceId) { mutableListOf() }.add(rf)

        val queue = ArrayDeque(ids)
        val seen = HashSet<Int>()
        val doneFiles = HashSet<String>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            for (rf in byId[id].orEmpty()) {
                val path = rf.filePath ?: continue
                if (!doneFiles.add(path) || path.endsWith(".9.png")) continue   // 9-patches would lose their chunks
                val bytes = try { rf.inputSource.openStream().use { it.readBytes() } } catch (_: Exception) { continue }
                val format = rasterFormat(bytes)
                if (format != null) {
                    try {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                        val out = ByteArrayOutputStream()
                        Recolour.apply(bmp, hue, strength, brightness).compress(format, if (format == Bitmap.CompressFormat.JPEG) 95 else 100, out)
                        module.add(ByteInputSource(out.toByteArray(), path))   // replaces the entry of the same name
                    } catch (_: Exception) { }
                    continue
                }
                val doc = try { rf.readAsXmlDocument() } catch (_: Exception) { null } ?: continue
                var changed = false
                val els = doc.recursiveElements()
                while (els.hasNext()) {
                    val attrs = (els.next() as ResXmlElement).attributes
                    while (attrs.hasNext()) {
                        val a = attrs.next() as ResXmlAttribute
                        val t = a.valueType ?: continue
                        if (t == ValueType.REFERENCE) {
                            if (isAppRes(a.data) && ids.add(a.data)) queue.add(a.data)   // foreground, layer, <bitmap src>…
                        } else if (t.isColor) {
                            a.data = Recolour.color(a.data, hue, strength, brightness)
                            a.valueType = ValueType.COLOR_ARGB8
                            changed = true
                        }
                    }
                }
                if (changed) module.add(ByteInputSource(doc.bytes, path))
            }
        }
    }

    // A raster image's format by its first bytes, or null for anything else (e.g. binary XML).
    private fun rasterFormat(b: ByteArray): Bitmap.CompressFormat? = when {
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() -> Bitmap.CompressFormat.PNG
        b.size > 12 && b[0] == 'R'.code.toByte() && b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() ->
            if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS
            else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> Bitmap.CompressFormat.JPEG
        else -> null
    }

    // An app's own resource (package id 0x7f), not the framework's (0x01) or unset (0).
    private fun isAppRes(id: Int) = id != 0 && (id ushr 24) == 0x7f

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
