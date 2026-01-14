package com.sms.edgenode.compliance

import android.content.Context

/**
 * Compliance checker for sender allowlists, content patterns, and IMEI/SIM binding.
 * Implements abuse prevention measures for carrier compliance.
 */
class ComplianceChecker(private val context: Context) {

    companion object {
        // Blocked content patterns (regex)
        private val SPAM_PATTERNS = listOf(
            "(?i)\\bfree\\s*money\\b",
            "(?i)\\bclaim\\s*your\\s*prize\\b",
            "(?i)\\bact\\s*now\\b.*\\bexpires\\b",
            "(?i)\\bcongratulations\\b.*\\bwon\\b"
        )

        // Premium number prefixes to block
        private val PREMIUM_PREFIXES = listOf("900", "901", "902", "0900", "0901", "0902")
    }

    /**
     * Check if a sender is allowed based on allowlist/blocklist.
     */
    fun isSenderAllowed(sender: String): Boolean {
        val prefs = context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
        
        // Check blocked senders (from kill switch)
        val blockedSenders = prefs.getStringSet("blocked_senders", emptySet()) ?: emptySet()
        if (blockedSenders.contains(sender)) {
            android.util.Log.w("ComplianceChecker", "Sender $sender is blocked")
            return false
        }

        // Check sender allowlist if enabled
        val allowlistEnabled = prefs.getBoolean("sender_allowlist_enabled", false)
        if (allowlistEnabled) {
            val allowlist = prefs.getStringSet("sender_allowlist", emptySet()) ?: emptySet()
            if (!allowlist.contains(sender) && !matchesAllowlistPattern(sender, allowlist)) {
                android.util.Log.w("ComplianceChecker", "Sender $sender not in allowlist")
                return false
            }
        }

        return true
    }

    /**
     * Check if content passes spam/pattern filters.
     */
    fun isContentAllowed(content: String): ContentCheckResult {
        // Check for spam patterns
        for (pattern in SPAM_PATTERNS) {
            if (content.matches(Regex(pattern))) {
                return ContentCheckResult(
                    allowed = false,
                    reason = "SPAM_PATTERN_DETECTED",
                    matchedPattern = pattern
                )
            }
        }

        // Check for phishing indicators
        if (containsPhishingIndicators(content)) {
            return ContentCheckResult(
                allowed = false,
                reason = "PHISHING_DETECTED"
            )
        }

        return ContentCheckResult(allowed = true)
    }

    /**
     * Check if destination is allowed (not premium, not blocked).
     */
    fun isDestinationAllowed(destination: String): Boolean {
        // Block premium numbers
        for (prefix in PREMIUM_PREFIXES) {
            if (destination.startsWith(prefix) || destination.startsWith("+254$prefix")) {
                android.util.Log.w("ComplianceChecker", "Premium number blocked: $destination")
                return false
            }
        }

        return true
    }

    /**
     * Verify IMEI + SIM binding if enabled.
     */
    fun verifyBinding(): Boolean {
        val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        
        if (!prefs.getBoolean("binding_enabled", false)) {
            return true // Binding not required
        }

        val storedImei = prefs.getString("bound_imei", null)

        if (storedImei == null) {
            return true // No binding set
        }

        // Verify current IMEI matches (requires READ_PHONE_STATE)
        val currentImei = getDeviceImei()
        if (currentImei != null && currentImei != storedImei) {
            android.util.Log.e("ComplianceChecker", "IMEI mismatch: expected $storedImei, got $currentImei")
            return false
        }

        return true
    }

    private fun matchesAllowlistPattern(sender: String, allowlist: Set<String>): Boolean {
        for (pattern in allowlist) {
            if (pattern.startsWith("regex:")) {
                try {
                    if (sender.matches(Regex(pattern.removePrefix("regex:")))) {
                        return true
                    }
                } catch (e: Exception) { /* Invalid regex */ }
            }
        }
        return false
    }

    private fun containsPhishingIndicators(content: String): Boolean {
        val indicators = listOf(
            "(?i)verify\\s*your\\s*account",
            "(?i)click\\s*here\\s*immediately",
            "(?i)your\\s*account\\s*will\\s*be\\s*suspended"
        )
        return indicators.any { content.matches(Regex(it)) }
    }

    @Suppress("DEPRECATION")
    private fun getDeviceImei(): String? {
        return try {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            telephony.deviceId
        } catch (e: SecurityException) {
            null
        }
    }

    data class ContentCheckResult(
        val allowed: Boolean,
        val reason: String? = null,
        val matchedPattern: String? = null
    )
}
