package com.sms.edgenode.data.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable

/**
 * Authentication service for V2 API.
 *
 * V2 Auth Flow:
 *   1. POST /mobile/login  → always returns { session_id, otp_required: true }
 *   2. POST /mobile/verify_otp  → { session_id, otp } → returns JWT tokens
 *   3. POST /mobile/refresh  → { refresh_token } → returns new JWT tokens
 *   4. POST /mobile/logout   → { refresh_token } → revokes tokens
 *
 * Tokens are securely stored in EncryptedSharedPreferences.
 */
class AuthService(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        EncryptedSharedPreferences.create(
            context,
            "auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_PUBLIC_ID = "user_public_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_ORG_ID = "organization_id"
        private const val KEY_ORG_PUBLIC_ID = "organization_public_id"
        private const val KEY_PREFERRED_SIM = "preferred_sim"
        private const val KEY_SMART_FAILOVER = "smart_failover"
        private const val KEY_CONTROL_SECRET = "control_security_key"
        private const val KEY_INVISIBLE_MODE = "invisible_mode"
        private const val KEY_USSD_AUTO_CHECK = "ussd_auto_check"
        private const val KEY_USSD_AIRTIME_CODE = "ussd_airtime_code"
        private const val KEY_USSD_SMS_CODE = "ussd_sms_code"
        private const val KEY_USSD_TOPUP_CODE = "ussd_topup_code"
        private const val KEY_USSD_BUNDLE_PURCHASE_CODE = "ussd_bundle_purchase_code"
        private const val KEY_USSD_AUTO_BUNDLE = "ussd_auto_bundle"
        private const val KEY_REFILL_THRESHOLD = "refill_threshold"
        private const val KEY_LAST_REFILL_TIME = "last_refill_time"
        private const val KEY_SERVICE_DISABLED = "service_disabled_by_user"
    }
    
    // ── V2 Response Models ──
    
    /**
     * V2 Login/Register response — always returns session_id for OTP flow.
     * The API never returns tokens directly from login.
     */
    @Serializable
    data class OtpResponse(
        val otp_required: Boolean = true,
        val session_id: String? = null,
        val expires_in: Long? = null,
        val message: String? = null
    )
    
    /**
     * V2 Token response — returned after OTP verification or token refresh.
     */
    @Serializable
    data class TokenResponse(
        val access_token: String? = null,
        val refresh_token: String? = null,
        val token_type: String? = null,
        val expires_in: Long? = null,
        val user_id: Long? = null,
        val user_public_id: String? = null,
        val organization_id: Long? = null,
        val organization_public_id: String? = null
    )
    
    /**
     * V2 Provisioning response — contains org encryption keys.
     */
    @Serializable
    data class ProvisioningResponse(
        val organization_public_id: String? = null,
        val control_security_key: String? = null,
        val webhook_signing_key: String? = null,
        val key_version: Int? = null
    )

    /**
     * V2 User profile response.
     */
    @Serializable
    data class UserProfile(
        val id: Long,
        val public_id: String? = null,
        val email: String,
        val name: String? = null,
        val organization_id: Long? = null,
        val organization_public_id: String? = null,
        val scopes: List<String> = emptyList()
    )

    /**
     * V2 Action response for logout, etc.
     */
    @Serializable
    data class ActionResponse(
        val status: String? = null,
        val message: String? = null
    )
    
    // ── Auth Operations ──
    
    /**
     * Step 1: Login with email/password.
     * V2 always requires OTP — returns a session_id (UUID) to use in verify_otp.
     */
    suspend fun login(email: String, password: String): Result<OtpResponse> {
        val response = HttpClient.post(
            ApiConfig.Endpoints.LOGIN,
            mapOf("email" to email, "password" to password)
        )
        android.util.Log.d("AuthService", "Login response: ${response.body}")
        
        return if (response.success) {
            HttpClient.parseV2Response<OtpResponse>(response)?.let {
                Result.success(it)
            } ?: Result.failure(Exception("Failed to parse login response"))
        } else {
            val errorMsg = extractV2Error(response.body) ?: "Login failed: ${response.code}"
            Result.failure(Exception(errorMsg))
        }
    }

    /**
     * Step 2: Verify OTP with session_id and 6-digit code.
     * Returns JWT access_token + refresh_token + org info.
     */
    suspend fun verifyOtp(sessionId: String, otp: String): Result<TokenResponse> {
        val response = HttpClient.post(
            ApiConfig.Endpoints.VERIFY_OTP,
            mapOf("session_id" to sessionId, "otp" to otp)
        )
        android.util.Log.d("AuthService", "Verify OTP response: ${response.body}")
        
        return if (response.success) {
            HttpClient.parseV2Response<TokenResponse>(response)?.let { tokenResponse ->
                saveTokens(tokenResponse)
                
                // Fetch provisioning data (security keys) after authentication
                fetchProvisioning()

                Result.success(tokenResponse)
            } ?: Result.failure(Exception("Failed to parse token response"))
        } else {
            val errorMsg = extractV2Error(response.body) ?: "OTP verification failed: ${response.code}"
            Result.failure(Exception(errorMsg))
        }
    }

    /**
     * Fetch user profile to get organization and scope info.
     */
    suspend fun fetchUserProfile(): Result<UserProfile> {
        val token = getAccessToken() ?: return Result.failure(Exception("No access token"))
        val response = HttpClient.get(ApiConfig.Endpoints.ME, token)
        android.util.Log.d("AuthService", "Profile response: ${response.body}")

        return if (response.success) {
            HttpClient.parseV2Response<UserProfile>(response)?.let { profile ->
                profile.organization_id?.let { orgId ->
                    android.util.Log.d("AuthService", "Fetched organizationId: $orgId")
                    setOrganizationId(orgId)
                }
                profile.organization_public_id?.let { pubId ->
                    setOrganizationPublicId(pubId)
                }
                Result.success(profile)
            } ?: Result.failure(Exception("Failed to parse profile"))
        } else {
            Result.failure(Exception("Failed to fetch profile: ${response.code}"))
        }
    }
    
    /**
     * Refresh the access token using refresh token.
     */
    suspend fun refreshToken(): Result<TokenResponse> {
        val refreshToken = getRefreshToken() ?: return Result.failure(Exception("No refresh token"))
        
        val response = HttpClient.post(
            ApiConfig.Endpoints.REFRESH,
            mapOf("refresh_token" to refreshToken)
        )
        
        return if (response.success) {
            HttpClient.parseV2Response<TokenResponse>(response)?.let { tokenResponse ->
                saveTokens(tokenResponse)
                fetchProvisioning()
                Result.success(tokenResponse)
            } ?: Result.failure(Exception("Failed to parse token response"))
        } else {
            // Refresh failed, clear tokens ONLY if it's a hard failure (401/403)
            if (response.code == 401 || response.code == 403) {
                clearTokens()
            }
            Result.failure(Exception("Token refresh failed: ${response.code}"))
        }
    }
    
    /**
     * Fetch provisioning data (security keys) separately.
     */
    suspend fun fetchProvisioning(): Result<ProvisioningResponse> {
        val token = getAccessToken() ?: return Result.failure(Exception("No access token"))
        val response = HttpClient.get(ApiConfig.Endpoints.PROVISIONING, token)
        
        return if (response.success) {
            HttpClient.parseV2Response<ProvisioningResponse>(response)?.let { provisioning ->
                provisioning.control_security_key?.let { key ->
                    android.util.Log.d("AuthService", "Received control security key via Provisioning")
                    setControlSecurityKey(key)
                }
                provisioning.organization_public_id?.let { pubId ->
                    setOrganizationPublicId(pubId)
                }
                Result.success(provisioning)
            } ?: Result.failure(Exception("Failed to parse provisioning response"))
        } else {
            Result.failure(Exception("Provisioning failed: ${response.code}"))
        }
    }

    /**
     * Logout — revoke tokens on server and clear local state.
     * V2 requires refresh_token in the body.
     */
    suspend fun logout(): Result<Boolean> {
        val token = getAccessToken()
        val refreshToken = getRefreshToken()
        if (token != null && refreshToken != null) {
            HttpClient.post(
                ApiConfig.Endpoints.LOGOUT,
                mapOf("refresh_token" to refreshToken),
                token
            )
        }
        clearTokens()
        return Result.success(true)
    }
    
    /**
     * Check if user is logged in (has valid token).
     */
    fun isLoggedIn(): Boolean {
        val hasAccessToken = getAccessToken() != null
        val hasRefreshToken = getRefreshToken() != null
        return hasAccessToken || hasRefreshToken
    }

    /**
     * Check if the access token is currently valid.
     */
    fun isTokenValid(): Boolean {
        val token = getAccessToken()
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        return token != null && (expiry == 0L || System.currentTimeMillis() < expiry)
    }
    
    // ── Token Accessors ──
    
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    
    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, -1L)

    fun getUserPublicId(): String? = prefs.getString(KEY_USER_PUBLIC_ID, null)
    
    fun getOrganizationId(): Long = prefs.getLong(KEY_ORG_ID, -1L)

    fun setOrganizationId(orgId: Long) {
        prefs.edit().putLong(KEY_ORG_ID, orgId).apply()
    }

    fun getOrganizationPublicId(): String? = prefs.getString(KEY_ORG_PUBLIC_ID, null)

    fun setOrganizationPublicId(pubId: String) {
        prefs.edit().putString(KEY_ORG_PUBLIC_ID, pubId).apply()
    }

    // ── Device/SIM Preferences ──

    fun getPreferredSimSlot(): Int = prefs.getInt(KEY_PREFERRED_SIM, -1)

    fun setPreferredSimSlot(slot: Int) {
        prefs.edit().putInt(KEY_PREFERRED_SIM, slot).apply()
    }

    fun isSmartFailoverEnabled(): Boolean = prefs.getBoolean(KEY_SMART_FAILOVER, true)

    fun setSmartFailoverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_FAILOVER, enabled).apply()
    }

    fun getControlSecurityKey(): String? {
        return prefs.getString(KEY_CONTROL_SECRET, null)
    }

    fun setControlSecurityKey(key: String) {
        prefs.edit().putString(KEY_CONTROL_SECRET, key).apply()
        // Re-initialize encryption immediately when key changes
        com.sms.edgenode.security.PayloadEncryption.initialize(key)
    }

    fun isInvisibleModeEnabled(): Boolean = prefs.getBoolean(KEY_INVISIBLE_MODE, false)

    fun setInvisibleModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INVISIBLE_MODE, enabled).apply()
    }

    fun isUssdAutoCheckEnabled(): Boolean = prefs.getBoolean(KEY_USSD_AUTO_CHECK, false)

    fun setUssdAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USSD_AUTO_CHECK, enabled).apply()
    }

    fun getUssdAirtimeCode(): String = prefs.getString(KEY_USSD_AIRTIME_CODE, "*144#") ?: "*144#"

    fun setUssdAirtimeCode(code: String) {
        prefs.edit().putString(KEY_USSD_AIRTIME_CODE, code).apply()
    }

    fun getUssdSmsCode(): String = prefs.getString(KEY_USSD_SMS_CODE, "*544*44#") ?: "*544*44#"

    fun setUssdSmsCode(code: String) {
        prefs.edit().putString(KEY_USSD_SMS_CODE, code).apply()
    }

    fun getUssdTopupCode(): String = prefs.getString(KEY_USSD_TOPUP_CODE, "*141*CARD#") ?: "*141*CARD#"

    fun setUssdTopupCode(code: String) {
        prefs.edit().putString(KEY_USSD_TOPUP_CODE, code).apply()
    }

    fun getUssdBundlePurchaseCode(): String = prefs.getString(KEY_USSD_BUNDLE_PURCHASE_CODE, "*544*2*1*1#") ?: "*544*2*1*1#"

    fun setUssdBundlePurchaseCode(code: String) {
        prefs.edit().putString(KEY_USSD_BUNDLE_PURCHASE_CODE, code).apply()
    }

    fun isAutoBundleEnabled(): Boolean = prefs.getBoolean(KEY_USSD_AUTO_BUNDLE, false)

    fun setAutoBundleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USSD_AUTO_BUNDLE, enabled).apply()
    }

    fun getRefillThreshold(): Double = prefs.getString(KEY_REFILL_THRESHOLD, "50.0")?.toDoubleOrNull() ?: 50.0

    fun setRefillThreshold(threshold: Double) {
        prefs.edit().putString(KEY_REFILL_THRESHOLD, threshold.toString()).apply()
    }

    fun getLastRefillRequestTime(): Long = prefs.getLong(KEY_LAST_REFILL_TIME, 0L)

    fun setLastRefillRequestTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_REFILL_TIME, timestamp).apply()
    }

    fun isServiceDisabledByUser(): Boolean = prefs.getBoolean(KEY_SERVICE_DISABLED, false)

    fun setServiceDisabledByUser(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_DISABLED, disabled).apply()
    }

    // ── Internal Helpers ──

    /**
     * Extract error message from V2 error envelope.
     * V2 errors: { "status_code": 4xx, "error": { "reason": "...", "message": "..." }, "meta": {...} }
     */
    private fun extractV2Error(body: String): String? {
        return try {
            val root = HttpClient.json.parseToJsonElement(body)
            val errorObj = root.jsonObject?.get("error")?.jsonObject
            errorObj?.get("message")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    private val kotlinx.serialization.json.JsonElement.jsonObject
        get() = this as? kotlinx.serialization.json.JsonObject

    private val kotlinx.serialization.json.JsonElement.jsonPrimitive
        get() = this as? kotlinx.serialization.json.JsonPrimitive
    
    private fun saveTokens(tokenResponse: TokenResponse) {
        prefs.edit().apply {
            tokenResponse.access_token?.let { putString(KEY_ACCESS_TOKEN, it) }
            tokenResponse.refresh_token?.let { putString(KEY_REFRESH_TOKEN, it) }
            tokenResponse.user_id?.let { putLong(KEY_USER_ID, it) }
            tokenResponse.user_public_id?.let { putString(KEY_USER_PUBLIC_ID, it) }
            tokenResponse.organization_id?.let { 
                android.util.Log.d("AuthService", "Saving organizationId: $it")
                putLong(KEY_ORG_ID, it) 
            }
            tokenResponse.organization_public_id?.let {
                android.util.Log.d("AuthService", "Saving organizationPublicId: $it")
                putString(KEY_ORG_PUBLIC_ID, it)
            }
            tokenResponse.expires_in?.let { 
                putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (it * 1000)) 
            }
            apply()
        }
    }
    
    private fun clearTokens() {
        prefs.edit().clear().apply()
    }
}
