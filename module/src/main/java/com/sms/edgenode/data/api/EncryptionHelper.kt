package com.sms.edgenode.data.api

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Encryption helper updated to support server v1 payloads.
 * - Produces v1:openssl:<base64(iv)>:<base64(tag)>:<base64(ciphertext)>
 * - Falls back to legacy format for compatibility: base64(iv):base64(ciphertext)
 */
object EncryptionHelper {

    private const val KEY_ALGORITHM = "AES"
    private const val SALT = "conduit-salt"
    private const val IV_LENGTH_GCM = 12 // recommended for GCM
    private const val TAG_LENGTH = 128 // bits
    private const val IV_LENGTH_LEGACY = 16

    /**
     * Legacy-compatible deriveKey: matches the original PHP "deriveKeyLegacy" behavior.
     * Returns ASCII bytes of first 32 hex chars of SHA256(orgId + SALT) — mirrors PHP legacy behavior.
     */
    private fun deriveKeyLegacy(organizationId: String): ByteArray {
        val input = organizationId + SALT
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))

        val hexHash = hash.joinToString("") { "%02x".format(it) }
        val keyString = hexHash.take(32)
        return keyString.toByteArray(Charsets.UTF_8)
    }

    /**
     * Encrypt data using AES-256-GCM and return a server v1 payload.
     */
    fun encrypt(data: String, organizationId: String): String {
        val key = deriveKeyLegacy(organizationId)
        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)

        val iv = ByteArray(IV_LENGTH_GCM)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val cipherOutput = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        // Java GCM output = ciphertext || tag (tag is last 16 bytes)
        val tagLenBytes = TAG_LENGTH / 8
        val tag = cipherOutput.takeLast(tagLenBytes).toByteArray()
        val ciphertext = cipherOutput.dropLast(tagLenBytes).toByteArray()

        return "v1:openssl:${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(tag, Base64.NO_WRAP)}:${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
    }

    /**
     * Decrypt payload produced by server or legacy clients.
     */
    fun decrypt(encryptedData: String, organizationId: String): String {
        // Handle v1:openssl:<iv>:<tag>:<ciphertext>
        if (encryptedData.startsWith("v1:openssl:")) {
            val parts = encryptedData.split(":")
            if (parts.size != 5) throw IllegalArgumentException("Invalid openssl payload format")

            val iv = Base64.decode(parts[2], Base64.NO_WRAP)
            val tag = Base64.decode(parts[3], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[4], Base64.NO_WRAP)

            val key = deriveKeyLegacy(organizationId)
            val keySpec = SecretKeySpec(key, KEY_ALGORITHM)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            // Java expects ciphertext||tag
            val combined = ByteArray(ciphertext.size + tag.size)
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.size)
            System.arraycopy(tag, 0, combined, ciphertext.size, tag.size)

            val decrypted = cipher.doFinal(combined)
            return String(decrypted, Charsets.UTF_8)
        }

        // Legacy format: base64(iv):base64(encrypted)
        val parts = encryptedData.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Invalid encrypted data format")

        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)

        val key = deriveKeyLegacy(organizationId)
        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }
}
