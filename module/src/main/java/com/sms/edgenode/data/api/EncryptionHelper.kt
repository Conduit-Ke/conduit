package com.sms.edgenode.data.api

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Encryption helper that matches the API's EncryptionService.
 * Uses AES-256-CBC with format: base64(IV):base64(encrypted)
 */
object EncryptionHelper {
    
    private const val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "AES"
    private const val SALT = "conduit-salt"
    private const val IV_LENGTH = 16
    
    /**
     * Derive encryption key from organization ID (matches PHP implementation).
     */
    private fun deriveKey(organizationId: String): ByteArray {
        val input = organizationId + SALT
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        
        // PHP takes first 32 chars of hex, then does base64 encode/decode dance
        // Which effectively just gives us the first 16 bytes in a weird way
        // Simplified: take first 32 chars of hex hash
        val hexHash = hash.joinToString("") { "%02x".format(it) }
        val keyString = hexHash.take(32)
        return keyString.toByteArray(Charsets.UTF_8)
    }
    
    /**
     * Encrypt data matching API's format: base64(IV):base64(encrypted)
     */
    fun encrypt(data: String, organizationId: String): String {
        val key = deriveKey(organizationId)
        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)
        
        // Generate random IV
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        // Format: base64(IV):base64(encrypted)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        
        return "$ivBase64:$encryptedBase64"
    }
    
    /**
     * Decrypt data from API's format.
     */
    fun decrypt(encryptedData: String, organizationId: String): String {
        val parts = encryptedData.split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid encrypted data format")
        }
        
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        
        val key = deriveKey(organizationId)
        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val ivSpec = IvParameterSpec(iv)
        
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }
}
