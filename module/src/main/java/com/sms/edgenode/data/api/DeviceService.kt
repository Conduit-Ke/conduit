package com.sms.edgenode.data.api

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.messaging.FirebaseMessaging
import com.sms.edgenode.observability.DeviceHealthMonitor
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Device registration and management service for V2 API.
 *
 * V2 changes:
 *   - No separate device_token. All requests use the user's JWT access_token.
 *   - Device registration via POST /v2/network/devices (returns public_id).
 *   - Heartbeat via POST /v2/system/devices/ping (flat JSON body).
 *   - Device identity is the hardware ANDROID_ID, sent as `device_id` in payloads.
 */
class DeviceService(private val context: Context, private val authService: AuthService) {

    @Serializable
    data class DevicePingRequest(
        val device_id: String,
        val status: String,
        val metadata: DevicePingMetadata? = null
    )

    @Serializable
    data class DevicePingMetadata(
        val fcm_token: String? = null,
        val battery_percentage: Int? = null,
        val is_charging: Boolean? = null,
        val has_connectivity: Boolean? = null,
        val airplane_mode: Boolean? = null,
        val sim_statuses: List<DeviceHealthMonitor.SimStatus>? = null,
        val memory_usage: DeviceHealthMonitor.MemoryInfo? = null,
        val uptime_ms: Long? = null,
        val os_version: String? = null,
        val app_version: String? = null
    )

    @Serializable
    data class DevicePingResponse(
        val acknowledged: Boolean? = null,
        val server_time: Long? = null,
        val config: DeviceConfig? = null
    )

    @Serializable
    data class DeviceConfig(
        val keywords: List<String> = emptyList(),
        val active_senders: List<String> = emptyList(),
        val control_security_key: String? = null
    )

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
        private const val KEY_DEVICE_PUBLIC_ID = "device_public_id"
        private const val KEY_DEVICE_ID = "device_identifier"
        private const val KEY_IS_REGISTERED = "is_registered"
    }
    
    /**
     * V2 Device model — mirrors the server's Device struct.
     */
    @Serializable
    data class Device(
        val id: Long? = null,
        val public_id: String? = null,
        val organization_id: Long? = null,
        val device_id: String? = null,
        val name: String? = null,
        val phone_number: String? = null,
        val status: String? = null
    )

    /**
     * V2 Create device request.
     */
    @Serializable
    data class CreateDeviceRequest(
        val device_id: String,
        val name: String? = null,
        val phone_number: String? = null
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
     * Register this device with the V2 API.
     * POST /v2/network/devices — authenticated by user JWT.
     */
    suspend fun register(
        deviceName: String,
        organizationId: Long
    ): Result<Device> {
        val token = authService.getAccessToken() 
            ?: return Result.failure(Exception("Not authenticated"))
        
        val deviceId = getHardwareIdentifier()

        val request = CreateDeviceRequest(
            device_id = deviceId,
            name = deviceName,
            phone_number = null  // Phone number can be updated later
        )

        val jsonBody = HttpClient.json.encodeToString(request)
        val response = HttpClient.postJson(
            ApiConfig.Endpoints.DEVICE_CREATE,
            jsonBody,
            token
        )
        
        return if (response.success) {
            HttpClient.parseV2Response<Device>(response)?.let { device ->
                // Store device public_id and identifier
                device.public_id?.let { pubId ->
                    saveDeviceInfo(pubId, deviceId)
                }
                authService.setOrganizationId(organizationId)
                Result.success(device)
            } ?: Result.failure(Exception("Failed to parse device registration response"))
        } else {
            val errorMsg = try {
                val root = HttpClient.json.parseToJsonElement(response.body)
                root.jsonObject?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content
            } catch (e: Exception) { null }
            Result.failure(Exception(errorMsg ?: "Registration failed: ${response.code}"))
        }
    }
    
    /**
     * Verify if the device is already registered on the server.
     * Lists devices and checks if our device_id exists.
     */
    suspend fun verify(): Result<Boolean> {
        val token = authService.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
        val identifier = getHardwareIdentifier()
        android.util.Log.d("DeviceService", "Verifying device identity: $identifier")
        
        val response = HttpClient.get(ApiConfig.Endpoints.DEVICE_LIST, token)
        android.util.Log.d("DeviceService", "Verify response code: ${response.code}")
        
        return if (response.success) {
            HttpClient.parseV2Response<List<Device>>(response)?.let { devices ->
                val myDevice = devices.find { it.device_id == identifier }
                if (myDevice != null) {
                    // Restore device session
                    myDevice.public_id?.let { pubId ->
                        android.util.Log.i("DeviceService", "Restoring device session for: $identifier")
                        saveDeviceInfo(pubId, identifier)
                    }
                    Result.success(true)
                } else {
                    android.util.Log.w("DeviceService", "Device $identifier not found in org device list")
                    Result.failure(Exception("Device not registered on server"))
                }
            } ?: Result.success(false)
        } else {
            android.util.Log.w("DeviceService", "Verification failed: ${response.code}")
            Result.failure(Exception("Verification failed: ${response.code}"))
        }
    }

    /**
     * Send heartbeat to V2 API with health data.
     * POST /v2/system/devices/ping — authenticated by user JWT.
     */
    suspend fun sendHeartbeat(snapshot: DeviceHealthMonitor.HealthSnapshot? = null): Result<DevicePingResponse> {
        val token = authService.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
        val deviceId = getDeviceIdentifier() ?: return Result.failure(Exception("No device identifier"))
        
        val fcmToken = try { 
            FirebaseMessaging.getInstance().token.await().also {
                android.util.Log.i("DeviceService", "Successfully retrieved FCM token during heartbeat")
            }
        } catch (e: Exception) { 
            android.util.Log.e("DeviceService", "Failed to retrieve FCM token during heartbeat", e)
            null 
        }

        val request = DevicePingRequest(
            device_id = deviceId,
            status = "online",
            metadata = DevicePingMetadata(
                fcm_token = fcmToken,
                battery_percentage = snapshot?.battery?.percentage,
                is_charging = snapshot?.battery?.isCharging,
                has_connectivity = snapshot?.hasConnectivity,
                airplane_mode = snapshot?.airplaneModeOn,
                sim_statuses = snapshot?.simStatuses,
                memory_usage = snapshot?.memoryUsage,
                uptime_ms = snapshot?.uptimeMs,
                os_version = Build.VERSION.RELEASE,
                app_version = getAppVersion()
            )
        )
        
        val jsonBody = HttpClient.json.encodeToString(request)
        val response = HttpClient.postJson(ApiConfig.Endpoints.DEVICE_PING, jsonBody, token)
        
        return if (response.success) {
            HttpClient.parseV2Response<DevicePingResponse>(response)?.let { pingResponse ->
                // Smartness: Persist keywords and active senders for edge filtering
                pingResponse.config?.let { config ->
                    saveDeviceConfig(config)
                    // Self-healing: Auto-provision control security key if present
                    config.control_security_key?.let { key ->
                        authService.setControlSecurityKey(key)
                        android.util.Log.i("DeviceService", "Control security key auto-provisioned via heartbeat")
                    }
                }
                Result.success(pingResponse)
            } ?: Result.failure(Exception("Failed to parse heartbeat response"))
        } else {
            Result.failure(Exception("Heartbeat failed: ${response.code}"))
        }
    }
    
    /**
     * Smartness: Determine if an incoming SMS should be forwarded to the server.
     * Logic: Forward if it matches an active keyword OR comes from an active session sender.
     */
    fun shouldForward(sender: String, body: String): Boolean {
        // 1. Always forward standard mobile numbers to support conversational workflows
        // (Standard numbers are > 7 digits and don't contain letters)
        val isStandardNumber = sender.length > 7 && !sender.any { it.isLetter() }
        if (isStandardNumber) return true

        // 2. Check Keywords (case-insensitive) for shorter/special numbers
        val keywords = getActiveKeywords()
        val matchesKeyword = keywords.any { kw -> 
            body.trim().split("\\s+".toRegex()).any { word -> word.equals(kw, ignoreCase = true) }
        }
        if (matchesKeyword) return true
        
        // 3. Check Active Senders (legacy/explicit session tracking)
        val activeSenders = getActiveSenders()
        val normalizedSender = sender.replace("+", "").replace(" ", "")
        val matchesSender = activeSenders.any { s -> 
            normalizedSender.endsWith(s.replace("+", "").replace(" ", ""))
        }
        
        return matchesSender
    }

    fun getActiveKeywords(): List<String> {
        return prefs.getStringSet("active_keywords", emptySet())?.toList() ?: emptyList()
    }

    fun getActiveSenders(): List<String> {
        return prefs.getStringSet("active_senders", emptySet())?.toList() ?: emptyList()
    }

    private fun saveDeviceConfig(config: DeviceConfig) {
        prefs.edit()
            .putStringSet("active_keywords", config.keywords.toSet())
            .putStringSet("active_senders", config.active_senders.toSet())
            .apply()
        android.util.Log.d("DeviceService", "Updated Device Config: ${config.keywords.size} keywords, ${config.active_senders.size} senders")
    }

    fun isRegistered(): Boolean = prefs.getBoolean(KEY_IS_REGISTERED, false)
    
    /**
     * Get the device's V2 public_id (UUIDv7).
     */
    fun getDevicePublicId(): String? = prefs.getString(KEY_DEVICE_PUBLIC_ID, null)
    
    /**
     * Get the hardware device identifier (ANDROID_ID).
     */
    fun getDeviceIdentifier(): String? = prefs.getString(KEY_DEVICE_ID, null)
    
    /**
     * Clear device registration state.
     */
    fun clearRegistration() {
        prefs.edit()
            .remove(KEY_DEVICE_PUBLIC_ID)
            .remove(KEY_IS_REGISTERED)
            .apply()
    }

    /**
     * Clear all device data.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    private fun saveDeviceInfo(publicId: String, identifier: String) {
        prefs.edit()
            .putString(KEY_DEVICE_PUBLIC_ID, publicId)
            .putString(KEY_DEVICE_ID, identifier)
            .putBoolean(KEY_IS_REGISTERED, true)
            .apply()
        android.util.Log.i("DeviceService", "Saved device publicId=$publicId for identifier=$identifier")
    }
    
    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.0.0"
        } catch (e: Exception) {
            "2.0.0"
        }
    }

    /**
     * Proactively sync FCM token with the server via a ping.
     */
    suspend fun syncFcmToken(fcmToken: String): Result<Boolean> {
        val token = authService.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
        val deviceId = getDeviceIdentifier() ?: return Result.failure(Exception("No device identifier"))

        val request = DevicePingRequest(
            device_id = deviceId,
            status = "online",
            metadata = DevicePingMetadata(fcm_token = fcmToken)
        )

        val jsonBody = HttpClient.json.encodeToString(request)
        val response = HttpClient.postJson(ApiConfig.Endpoints.DEVICE_PING, jsonBody, token)

        return if (response.success) {
            Result.success(true)
        } else {
            Result.failure(Exception("FCM token sync failed: ${response.code}"))
        }
    }

    // ── JSON helpers (inline with AuthService pattern) ──
    private val kotlinx.serialization.json.JsonElement.jsonObject
        get() = this as? kotlinx.serialization.json.JsonObject

    private val kotlinx.serialization.json.JsonElement.jsonPrimitive
        get() = this as? kotlinx.serialization.json.JsonPrimitive
}
