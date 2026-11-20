package com.handball.stattracker.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verschlüsselt Geheimnisse (Login-Token) mit einem AES-256-GCM-Schlüssel im Android Keystore.
 * Der Schlüssel verlässt den Keystore nie (nicht auslesbar, an die App gebunden); auf der Platte
 * liegt nur das Chiffrat im Format `v1:<iv>:<daten>` (Base64).
 */
internal object AndroidSecretCipher {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "handball_stat_tracker_secret_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "v1"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** null, wenn der Keystore nicht verfügbar ist; dann wird nichts im Klartext gespeichert. */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        listOf(PREFIX, Base64.encodeToString(cipher.iv, Base64.NO_WRAP), Base64.encodeToString(data, Base64.NO_WRAP))
            .joinToString(":")
    }.getOrNull()

    /** null bei fehlendem/ungültigem Schlüssel oder beschädigten Daten (z. B. nach Neuinstallation). */
    fun decrypt(sealed: String): String? = runCatching {
        val parts = sealed.trim().split(':')
        if (parts.size != 3 || parts[0] != PREFIX) return null
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val data = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()
}
