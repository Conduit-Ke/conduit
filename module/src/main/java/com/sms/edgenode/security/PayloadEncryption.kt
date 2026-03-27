package com.sms.edgenode.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM encryption/decryption for FCM payloads and API data.
 * Produces server-compatible v1:openssl format payloads.
 */
object PayloadEncryption {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH = 12

    // Key derived from the shared secret
    private var encryptionKey: ByteArray? = null

    fun initialize(secret: String) {
        // Consistently use SHA-256 hash of the secret string to derive the 32-byte key.
        // This matches the PHP backend: hash('sha256', $key, true)
        try {
            encryptionKey = java.security.MessageDigest.getInstance("SHA-256")
                .digest(secret.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            android.util.Log.e("PayloadEncryption", "Failed to initialize: ${e.message}")
        }
    }

    fun encrypt(plaintext: String): String? {
        val key = encryptionKey ?: return null
        
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            
            val cipherOutput = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            
            val tagLenBytes = TAG_LENGTH_BITS / 8
            val tag = cipherOutput.takeLast(tagLenBytes).toByteArray()
            val ciphertext = cipherOutput.dropLast(tagLenBytes).toByteArray()

            "v1:openssl:${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(tag, Base64.NO_WRAP)}:${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
        } catch (e: Exception) {
            android.util.Log.e("PayloadEncryption", "Encryption failed: ${e.message}")
            null
        }
    }

    fun decrypt(encryptedPayload: String): String? {
        val key = encryptionKey ?: return null
        
        if (!encryptedPayload.startsWith("v1:openssl:")) return null

        return try {
            val parts = encryptedPayload.split(":")
            if (parts.size != 5) return null

            val iv = Base64.decode(parts[2], Base64.NO_WRAP)
            val tag = Base64.decode(parts[3], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[4], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)

            val combined = ByteArray(ciphertext.size + tag.size)
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.size)
            System.arraycopy(tag, 0, combined, ciphertext.size, tag.size)

            String(cipher.doFinal(combined), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("PayloadEncryption", "Decryption failed: ${e.message}")
            null
        }
    }
}
