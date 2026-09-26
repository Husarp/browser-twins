package com.husarp.browsertwins

import android.content.Context
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

// Browser Twins' own signing key. Every clone is signed with it, and every update of a clone must use
// the SAME key or Android refuses the update (and the clone's data would be lost). So the key is made
// once, kept in the app's private files (PKCS12, built into Android), and can be backed up (see TODO).
class Keys(ctx: Context) {

    private val file = File(ctx.filesDir, "signing.p12")
    private val pass = "browsertwins".toCharArray()   // file is in app-private storage; real backup: TODO
    private val alias = "browsertwins"

    data class Signer(val privateKey: PrivateKey, val certificate: X509Certificate)

    // Copy the signing key out to a file the user picked (make it first if needed). Losing this key
    // means clones can no longer be updated without losing their data, so it is worth backing up.
    fun exportTo(out: java.io.OutputStream) {
        if (!file.exists()) signer()   // make sure the key exists before backing it up
        file.inputStream().use { it.copyTo(out) }
    }

    // Replace the signing key from a backup the user picked.
    fun importFrom(inp: java.io.InputStream) {
        file.outputStream().use { inp.copyTo(it) }
    }

    fun signer(): Signer {
        val store = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            file.inputStream().use { store.load(it, pass) }
        } else {
            store.load(null, null)
            val (key, cert) = generate()
            store.setKeyEntry(alias, key, pass, arrayOf(cert))
            file.outputStream().use { store.store(it, pass) }
        }
        return Signer(store.getKey(alias, pass) as PrivateKey, store.getCertificate(alias) as X509Certificate)
    }

    private fun generate(): Pair<PrivateKey, X509Certificate> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Principal("CN=Browser Twins")
        val from = Date()
        val to = Date(from.time + 30L * 365 * 24 * 60 * 60 * 1000)   // ~30 years
        val builder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(System.currentTimeMillis()), from, to, name, kp.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return kp.private to cert
    }
}
