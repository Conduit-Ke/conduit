package com.sms.edgenode.security

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import android.util.Base64

object SignatureVerifier {
    fun verify(data: String, signatureHex: String, secret: String): Boolean {
        return try {
            val key = secret.toByteArray()
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
            val hmac = mac.doFinal(data.toByteArray())
            val hex = hmac.joinToString("") { "%02x".format(it) }
            
            java.security.MessageDigest.isEqual(
                hex.lowercase().toByteArray(Charsets.UTF_8),
                signatureHex.lowercase().toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            false
        }
    }
}
