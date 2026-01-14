package com.sms.edgenode.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Tamper detection module for:
 * - Root detection
 * - Debugger attachment detection
 * - Signature verification
 * - Emulator detection
 */
object TamperDetection {
    
    data class SecurityStatus(
        val isRooted: Boolean,
        val isDebugged: Boolean,
        val signatureValid: Boolean,
        val isEmulator: Boolean,
        val threatLevel: ThreatLevel
    )

    enum class ThreatLevel {
        SAFE,
        LOW,      // Minor concerns (e.g., running on emulator)
        MEDIUM,   // Moderate (e.g., debugger attached)
        HIGH,     // Serious (e.g., rooted device)
        CRITICAL  // Multiple threats detected
    }

    /**
     * Perform comprehensive security check.
     */
    fun checkSecurity(context: Context): SecurityStatus {
        val isRooted = checkRoot()
        val isDebugged = checkDebugger()
        val signatureValid = verifySignature(context)
        val isEmulator = checkEmulator()

        val threats = listOf(isRooted, isDebugged, !signatureValid, isEmulator).count { it }
        val threatLevel = when {
            threats >= 3 -> ThreatLevel.CRITICAL
            isRooted -> ThreatLevel.HIGH
            isDebugged || !signatureValid -> ThreatLevel.MEDIUM
            isEmulator -> ThreatLevel.LOW
            else -> ThreatLevel.SAFE
        }

        return SecurityStatus(
            isRooted = isRooted,
            isDebugged = isDebugged,
            signatureValid = signatureValid,
            isEmulator = isEmulator,
            threatLevel = threatLevel
        )
    }

    /**
     * Check for root indicators.
     */
    private fun checkRoot(): Boolean {
        // Check for su binary
        val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        
        if (suPaths.any { File(it).exists() }) {
            return true
        }

        // Check build tags
        if (Build.TAGS?.contains("test-keys") == true) {
            return true
        }

        try {
            val pm = Runtime.getRuntime().exec("which su")
            if (pm.inputStream.bufferedReader().readLine() != null) {
                return true
            }
        } catch (e: Exception) { /* Ignore */ }

        return false
    }

    /**
     * Check if debugger is attached.
     */
    private fun checkDebugger(): Boolean {
        return android.os.Debug.isDebuggerConnected() ||
               android.os.Debug.waitingForDebugger()
    }

    /**
     * Verify app signature matches expected.
     */
    private fun verifySignature(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            // For production, compare against hardcoded hash
            // For now, just verify signature exists
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }

            !signatures.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check for emulator indicators.
     */
    private fun checkEmulator(): Boolean {
        val indicators = listOf(
            Build.FINGERPRINT.startsWith("generic"),
            Build.FINGERPRINT.startsWith("unknown"),
            Build.MODEL.contains("google_sdk"),
            Build.MODEL.contains("Emulator"),
            Build.MODEL.contains("Android SDK built for x86"),
            Build.MANUFACTURER.contains("Genymotion"),
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"),
            Build.PRODUCT == "sdk" || Build.PRODUCT == "sdk_x86" || Build.PRODUCT == "sdk_google",
            Build.HARDWARE.contains("goldfish"),
            Build.HARDWARE.contains("ranchu")
        )

        return indicators.count { it } >= 2
    }
}
