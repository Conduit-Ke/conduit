package com.sms.edgenode.data.api

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable

/**
 * Device registration and management service.
 */
class DeviceService(private val context: Context, private val authService: AuthService) {
    
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        EncryptedSharedPreferences.create(
            context,
            "device_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    companion object {
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_ID = "device_identifier"
        private const val KEY_IS_REGISTERED = "is_registered"
    }
    
    @Serializable
    data class DeviceRegistrationResponse(
        val success: Boolean,
        val message: String? = null,
        val device: DeviceInfo? = null,
        val error: String? = null
    )
    
    @Serializable
    data class DeviceInfo(
        val id: Int? = null,
        val identifier: String? = null,
        val name: String? = null,
        val platform: String? = null,
        val is_active: Boolean? = null,
        val organization_id: Int? = null,
        val device_token: String? = null
    )
    
    /**
     * Get a stable hardware identifier for this device.
     */
    suspend fun getHardwareIdentifier(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN_DEVICE_${System.currentTimeMillis()}"
    }
    
    /**
     * Register this device with the API.
     */
    suspend fun register(
        deviceName: String,
        organizationId: Int
    ): Result<DeviceRegistrationResponse> {
        val token = authService.getAccessToken() 
            ?: return Result.failure(Exception("Not authenticated"))
        
        val fiid = getHardwareIdentifier()
        val fcmToken = try { FirebaseMessaging.getInstance().token.await() } catch (e: Exception) { null }
        
        val params = mutableMapOf(
            "fiid" to fiid,
            "name" to deviceName,
            "organization_id" to organizationId.toString(),
            "platform" to "android",
            "os_version" to Build.VERSION.RELEASE,
            "app_version" to getAppVersion()
        )
        
        fcmToken?.let { params["fcm_token"] = it }
        
        val response = HttpClient.post(
            ApiConfig.Endpoints.DEVICE_REGISTER,
            params,
            token
        )
        
        return if (response.success) {
            HttpClient.parseResponse<DeviceRegistrationResponse>(response)?.let { regResponse ->
                // Store device token securely
                regResponse.device?.device_token?.let { deviceToken ->
                    saveDeviceToken(deviceToken, regResponse.device.identifier ?: fiid)
                }
                authService.setOrganizationId(organizationId)
                Result.success(regResponse)
            } ?: Result.failure(Exception("Failed to parse registration response"))
        } else {
            val error = try {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString<DeviceRegistrationResponse>(response.body)
            } catch (e: Exception) { null }
            Result.failure(Exception(error?.message ?: error?.error ?: "Registration failed: ${response.code}"))
        }
    }
    
    /**
     * Verify if the device is already registered on the server.
     */
    suspend fun verify(): Result<Boolean> {
        val token = authService.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
        val identifier = getHardwareIdentifier()
        android.util.Log.d("DeviceService", "Verifying device identity: $identifier")
        
        // Correct endpoint: /api/devices/{identifier}/verify
        val endpoint = "/devices/$identifier/verify"
        val response = HttpClient.get(endpoint, token)
        android.util.Log.d("DeviceService", "Verify response code: ${response.code}")
        
        return if (response.success) {
            HttpClient.parseResponse<DeviceRegistrationResponse>(response)?.let { regResponse ->
                // Restore device session if token provided
                regResponse.device?.device_token?.let { deviceToken ->
                    android.util.Log.i("DeviceService", "Restoring device session for: $identifier")
                    saveDeviceToken(deviceToken, regResponse.device.identifier ?: identifier)
                }
                prefs.edit().putBoolean(KEY_IS_REGISTERED, true).putString(KEY_DEVICE_ID, identifier).apply()
                Result.success(true)
            } ?: Result.success(true) // Fallback if no token but 200
        } else {
            android.util.Log.w("DeviceService", "Verification failed: ${response.code}")
            Result.failure(Exception("Verification failed: ${response.code}"))
        }
    }

    /**
     * Send heartbeat to API.
     */
    suspend fun sendHeartbeat(): Result<Boolean> {
        val deviceToken = getDeviceToken() ?: return Result.failure(Exception("Device not registered"))
        val deviceId = getDeviceIdentifier() ?: return Result.failure(Exception("No device identifier"))
        val fcmToken = try { FirebaseMessaging.getInstance().token.await() } catch (e: Exception) { null }
        
        var endpoint = ApiConfig.Endpoints.DEVICE_STATUS.replace("{identifier}", deviceId)
        if (fcmToken != null) {
            endpoint += "?fcm_token=$fcmToken"
        }
        
        val response = HttpClient.get(endpoint, deviceToken)
        
        return if (response.success) {
            Result.success(true)
        } else {
            Result.failure(Exception("Heartbeat failed: ${response.code}"))
        }
    }
    
    fun isRegistered(): Boolean = prefs.getBoolean(KEY_IS_REGISTERED, false)
    
    fun getDeviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)
    
    fun getDeviceIdentifier(): String? = prefs.getString(KEY_DEVICE_ID, null)
    
    /**
     * Clear session token but keep device identity.
     */
    fun clear() {
        prefs.edit().remove(KEY_DEVICE_TOKEN).apply()
    }
    
    private fun saveDeviceToken(token: String, identifier: String) {
        prefs.edit().apply {
            putString(KEY_DEVICE_TOKEN, token)
            putString(KEY_DEVICE_ID, identifier)
            putBoolean(KEY_IS_REGISTERED, true)
            apply()
        }
    }
    
    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Proactively sync FCM token with the server.
     */
    suspend fun syncFcmToken(fcmToken: String): Result<Boolean> {
        val deviceToken = getDeviceToken() ?: return Result.failure(Exception("Device not registered"))
        val deviceId = getDeviceIdentifier() ?: return Result.failure(Exception("No device identifier"))

        val endpoint = ApiConfig.Endpoints.DEVICE_STATUS.replace("{identifier}", deviceId) + "/fcm-token"
        val response = HttpClient.post(
            endpoint,
            mapOf("fcm_token" to fcmToken),
            deviceToken
        )

        return if (response.success) {
            Result.success(true)
        } else {
            Result.failure(Exception("FCM token sync failed: ${response.code}"))
        }
    }
}
