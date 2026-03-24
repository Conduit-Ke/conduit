package com.sms.edgenode.data.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable

/**
 * Authentication service for login, OTP verification, and token management.
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
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_ORG_ID = "organization_id"
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
    }
    
    @Serializable
    data class LoginResponse(
        val otp_required: Boolean = false,
        val user_id: Int? = null,
        val email: String? = null,
        val message: String? = null,
        val error: String? = null
    )
    
    @Serializable
    data class TokenResponse(
        val access_token: String? = null,
        val token_type: String? = null,
        val expires_in: Long? = null,
        val refresh_token: String? = null,
        val organization_id: Int? = null,
        val control_security_key: String? = null,
        val error: String? = null
    )
    
    @Serializable
    data class ProvisioningResponse(
        val control_security_key: String? = null,
        val error: String? = null
    )
    
    /**
     * Step 1: Login with email/password. Returns user_id if OTP required.
     */
    suspend fun login(email: String, password: String): Result<LoginResponse> {
        val response = HttpClient.post(
            ApiConfig.Endpoints.LOGIN,
            mapOf("email" to email, "password" to password)
        )
        android.util.Log.d("AuthService", "Login response: ${response.body}")
        
        return if (response.success) {
            val body = response.body
            if (body.contains("access_token")) {
                HttpClient.parseResponse<TokenResponse>(response)?.let { tokenResponse ->
                    saveTokens(tokenResponse)
                    // We don't have user_id here but we can at least return a successful LoginResponse
                    Result.success(LoginResponse(otp_required = false))
                } ?: Result.failure(Exception("Failed to parse token response"))
            } else {
                HttpClient.parseResponse<LoginResponse>(response)?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Failed to parse response"))
            }
        } else {
            val error = try {
                kotlinx.serialization.json.Json.decodeFromString<LoginResponse>(response.body)
            } catch (e: Exception) { null }
            Result.failure(Exception(error?.error ?: "Login failed: ${response.code}"))
        }
    }

    @Serializable
    data class UserProfile(
        val id: Int,
        val email: String,
        val organization_id: Int? = null,
        val organization: Organization? = null
    )

    @Serializable
    data class Organization(
        val id: Int,
        val name: String
    )

    /**
     * Fetch user profile to get organization ID.
     */
    suspend fun fetchUserProfile(): Result<UserProfile> {
        val token = getAccessToken() ?: return Result.failure(Exception("No access token"))
        val response = HttpClient.post(ApiConfig.Endpoints.ME, emptyMap(), token)
        android.util.Log.d("AuthService", "Profile response: ${response.body}")

        return if (response.success) {
            HttpClient.parseResponse<UserProfile>(response)?.let { profile ->
                val orgId = profile.organization_id ?: profile.organization?.id
                orgId?.let { 
                    android.util.Log.d("AuthService", "Fetched organizationId: $it")
                    setOrganizationId(it) 
                }
                Result.success(profile)
            } ?: Result.failure(Exception("Failed to parse profile"))
        } else {
            Result.failure(Exception("Failed to fetch profile: ${response.code}"))
        }
    }
    
    /**
     * Step 2: Verify OTP and get JWT token.
     */
    suspend fun verifyOtp(userId: Int, otp: String): Result<TokenResponse> {
        val response = HttpClient.post(
            ApiConfig.Endpoints.VERIFY_OTP,
            mapOf("user_id" to userId.toString(), "otp" to otp)
        )
        android.util.Log.d("AuthService", "Verify OTP response: ${response.body}")
        
        return if (response.success) {
            HttpClient.parseResponse<TokenResponse>(response)?.let { tokenResponse ->
                // Store tokens securely
                saveTokens(tokenResponse)
                
                // Fetch provisioning data (security keys) independently
                fetchProvisioning()

                prefs.edit().putInt(KEY_USER_ID, userId).apply()
                Result.success(tokenResponse)
            } ?: Result.failure(Exception("Failed to parse token response"))
        } else {
            val error = try {
                kotlinx.serialization.json.Json.decodeFromString<TokenResponse>(response.body)
            } catch (e: Exception) { null }
            Result.failure(Exception(error?.error ?: "OTP verification failed: ${response.code}"))
        }
    }
    
    /**
     * Resend OTP.
     */
    suspend fun resendOtp(userId: Int): Result<String> {
        val response = HttpClient.post(
            ApiConfig.Endpoints.RESEND_OTP,
            mapOf("user_id" to userId.toString())
        )
        
        return if (response.success) {
            Result.success("OTP sent successfully")
        } else {
            Result.failure(Exception("Failed to resend OTP"))
        }
    }
    
    /**
     * Refresh the access token using refresh token.
     */
    suspend fun refreshToken(): Result<TokenResponse> {
        val refreshToken = getRefreshToken() ?: return Result.failure(Exception("No refresh token"))
        
        val response = HttpClient.post(
            ApiConfig.Endpoints.REFRESH,
            mapOf("refresh_token" to refreshToken),
            getAccessToken()
        )
        
        return if (response.success) {
            HttpClient.parseResponse<TokenResponse>(response)?.let { tokenResponse ->
                saveTokens(tokenResponse)
                fetchProvisioning()
                Result.success(tokenResponse)
            } ?: Result.failure(Exception("Failed to parse token response"))
        } else {
            // Refresh failed, clear tokens
            clearTokens()
            Result.failure(Exception("Token refresh failed"))
        }
    }
    
    /**
     * Fetch provisioning data (security keys) separately.
     */
    suspend fun fetchProvisioning(): Result<ProvisioningResponse> {
        val token = getAccessToken() ?: return Result.failure(Exception("No access token"))
        val response = HttpClient.get(ApiConfig.Endpoints.PROVISIONING, token)
        
        return if (response.success) {
            HttpClient.parseResponse<ProvisioningResponse>(response)?.let { provisioning ->
                provisioning.control_security_key?.let { key ->
                    android.util.Log.d("AuthService", "Received control security key via Provisioning")
                    setControlSecurityKey(key)
                }
                Result.success(provisioning)
            } ?: Result.failure(Exception("Failed to parse provisioning response"))
        } else {
            Result.failure(Exception("Provisioning failed: ${response.code}"))
        }
    }

    /**
     * Logout - clear all tokens.
     */
    suspend fun logout(): Result<Boolean> {
        val token = getAccessToken()
        if (token != null) {
            HttpClient.post(ApiConfig.Endpoints.LOGOUT, emptyMap(), token)
        }
        clearTokens()
        return Result.success(true)
    }
    
    /**
     * Check if user is logged in (has valid token).
     */
    fun isLoggedIn(): Boolean {
        val token = getAccessToken()
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        return token != null && (expiry == 0L || System.currentTimeMillis() < expiry)
    }
    
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    
    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)
    
    fun getOrganizationId(): Int = prefs.getInt(KEY_ORG_ID, -1)
    
    fun setOrganizationId(orgId: Int) {
        prefs.edit().putInt(KEY_ORG_ID, orgId).apply()
    }

    fun getPreferredSimSlot(): Int = prefs.getInt(KEY_PREFERRED_SIM, -1)

    fun setPreferredSimSlot(slot: Int) {
        prefs.edit().putInt(KEY_PREFERRED_SIM, slot).apply()
    }

    fun isSmartFailoverEnabled(): Boolean = prefs.getBoolean(KEY_SMART_FAILOVER, true)

    fun setSmartFailoverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_FAILOVER, enabled).apply()
    }

    fun getControlSecurityKey(): String? {
        // Return hardcoded placeholder for now, but allow setting it via provisioning
        return prefs.getString(KEY_CONTROL_SECRET, env_placeholder_key())
    }

    fun setControlSecurityKey(key: String) {
        prefs.edit().putString(KEY_CONTROL_SECRET, key).apply()
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

    private fun env_placeholder_key(): String? {
        // Dynamic Provisioning: Key must now be received via OTP flow.
        // Returning null forces the system to fail until properly provisioned.
        return null
    }
    
    private fun saveTokens(tokenResponse: TokenResponse) {
        prefs.edit().apply {
            tokenResponse.access_token?.let { putString(KEY_ACCESS_TOKEN, it) }
            tokenResponse.refresh_token?.let { putString(KEY_REFRESH_TOKEN, it) }
            tokenResponse.organization_id?.let { 
                android.util.Log.d("AuthService", "Saving organizationId: $it")
                putInt(KEY_ORG_ID, it) 
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
