package com.sms.edgenode.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM encryption/decryption for sensitive FCM payloads.
 * Uses a pre-shared symmetric key (stored securely on device).
 */
object PayloadEncryption {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128

    // Key derived from the shared secret
    private var encryptionKey: ByteArray? = null

    fun initialize(secret: String) {
        // Derive 256-bit AES key from the secret string
        encryptionKey = java.security.MessageDigest.getInstance("SHA-256")
            .digest(secret.toByteArray())
    }

    fun decrypt(encryptedBase64: String, nonceBase64: String): String? {
        val key = encryptionKey ?: return null
        
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val nonce = Base64.decode(nonceBase64, Base64.DEFAULT)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            // In Java, GCMParameterSpec handles the tag at the end of the input buffer
            val spec = GCMParameterSpec(TAG_LENGTH, nonce)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            
            String(cipher.doFinal(combined), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("PayloadEncryption", "Decryption failed: ${e.message}")
            null
        }
    }

    fun encrypt(plaintext: String, nonce: ByteArray): String? {
        val key = encryptionKey ?: return null
        
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("PayloadEncryption", "Encryption failed: ${e.message}")
            null
        }
    }
}
