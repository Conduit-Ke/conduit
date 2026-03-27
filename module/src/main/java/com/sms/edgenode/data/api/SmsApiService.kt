package com.sms.edgenode.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SMS API service for submitting received SMS and status updates.
 * Data is encrypted before sending.
 */
class SmsApiService(private val deviceService: DeviceService) {
    
    private val _quotaInfo = MutableStateFlow<QuotaInfo?>(null)
    val quotaInfo: StateFlow<QuotaInfo?> = _quotaInfo.asStateFlow()

    fun updateQuota(newQuota: QuotaInfo?) {
        newQuota?.let { _quotaInfo.value = it }
    }
    
    @Serializable
    data class ApiWrapper<T>(
        val success: Boolean,
        val message: String? = null,
        val data: T? = null
    )

    @Serializable
    data class OrgBalance(
        val amount: Double,
        val currency: String
    )

    @Serializable
    data class QuotaDetail(
        val allowed: Boolean,
        val limit: String, // Can be "unlimited" or number
        val used: Int,
        val remaining: String, // Can be "unlimited" or number
        val balance: OrgBalance? = null
    )

    @Serializable
    data class QuotaInfo(
        val received: QuotaDetail? = null,
        val sent: QuotaDetail? = null,
        val balance: OrgBalance? = null
    )

    @Serializable
    data class SmsSubmitResponse(
        val success: Boolean = true,
        val message_id: String? = null,
        val message: String? = null,
        val error: String? = null,
        val duplicate: Boolean = false,
        val quota: QuotaInfo? = null
    )
    
    @Serializable
    data class SmsStatusResponse(
        val success: Boolean = true,
        val message: String? = null,
        val quota: QuotaInfo? = null
    )

    @Serializable
    data class StatusUpdate(
        val phone_number: String,
        val message: String,
        val status: String,
        val error: String? = null,
        val request_id: String? = null,
        val timestamp: Long,
        val sim_slot: Int? = null,
        val carrier: String? = null
    )

    @Serializable
    data class StatusBatchRequest(
        val updates: List<StatusUpdate>
    )

    @Serializable
    data class SmsBatchItem(
        val sender: String,
        val message: String,
        val message_id: String,
        val destination: String,
        val sent_timestamp: Long
    )

    @Serializable
    data class SmsBatchRequest(
        val batch: List<SmsBatchItem>
    )

    @Serializable
    data class SmsBatchResponse(
        val success: Boolean = true,
        val results: List<SmsSubmitResponse>? = null,
        val quota: QuotaInfo? = null,
        val error: String? = null
    )
    
    /**
     * Submit received SMS to API (encrypted).
     */
    suspend fun submitSms(
        sender: String,
        message: String,
        messageId: String,
        destination: String,
        sentTimestamp: Long,
        organizationId: String
    ): Result<SmsSubmitResponse> {
        val deviceToken = deviceService.getDeviceToken()
            ?: return Result.failure(Exception("Device not registered"))
        
        val deviceId = deviceService.getDeviceIdentifier()
            ?: return Result.failure(Exception("Device identifier not found"))
        
        // Encrypt sensitive data
        val encryptedSender = com.sms.edgenode.security.PayloadEncryption.encrypt(sender)
            ?: return Result.failure(Exception("Payload encryption failed for sender"))
        val encryptedMessage = com.sms.edgenode.security.PayloadEncryption.encrypt(message)
            ?: return Result.failure(Exception("Payload encryption failed for message"))
        
        val endpoint = ApiConfig.Endpoints.SMS_RECEIVE.replace("{identifier}", deviceId)
        val response = HttpClient.post(
            endpoint,
            mapOf(
                "sender" to encryptedSender,
                "message" to encryptedMessage,
                "message_id" to messageId,
                "destination" to destination,
                "sent_timestamp" to sentTimestamp.toString()
            ),
            deviceToken
        )
        
        return if (response.success) {
            HttpClient.parseResponse<ApiWrapper<SmsSubmitResponse>>(response)?.data?.let {
                updateQuota(it.quota)
                Result.success(it)
            } ?: Result.failure(Exception("Failed to parse response"))
        } else {
            Result.failure(Exception("SMS submission failed: ${response.code}"))
        }
    }

    /**
     * Submit a batch of received SMS to API (encrypted).
     */
    suspend fun submitBatch(
        batch: List<SmsBatchItem>,
        organizationId: String
    ): Result<SmsBatchResponse> {
        val deviceToken = deviceService.getDeviceToken()
            ?: return Result.failure(Exception("Device not registered"))
        
        val deviceId = deviceService.getDeviceIdentifier()
            ?: return Result.failure(Exception("Device identifier not found"))

        val encryptedBatch = try {
            batch.map { item ->
                val encSender = com.sms.edgenode.security.PayloadEncryption.encrypt(item.sender)
                    ?: throw Exception("Payload encryption failed for batch sender")
                val encMessage = com.sms.edgenode.security.PayloadEncryption.encrypt(item.message)
                    ?: throw Exception("Payload encryption failed for batch message")
                
                item.copy(
                    sender = encSender,
                    message = encMessage
                )
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val jsonBody = HttpClient.json.encodeToString(SmsBatchRequest(encryptedBatch))
        val endpoint = ApiConfig.Endpoints.SMS_BATCH.replace("{identifier}", deviceId)
        val response = HttpClient.postJson(
            endpoint,
            jsonBody,
            deviceToken
        )

        return if (response.success) {
            HttpClient.parseResponse<ApiWrapper<SmsBatchResponse>>(response)?.data?.let {
                updateQuota(it.quota)
                Result.success(it)
            } ?: Result.success(SmsBatchResponse(true))
        } else {
            Result.failure(Exception("SMS batch submission failed: ${response.code}"))
        }
    }
    
    /**
     * Submit SMS send status (for outgoing SMS).
     */
    suspend fun submitStatus(
        phoneNumber: String,
        message: String,
        status: String,
        error: String? = null,
        requestId: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Result<SmsStatusResponse> {
        val deviceToken = deviceService.getDeviceToken()
            ?: return Result.failure(Exception("Device not registered"))
        
        val deviceId = deviceService.getDeviceIdentifier()
            ?: return Result.failure(Exception("Device identifier not found"))
        
        val endpoint = ApiConfig.Endpoints.SMS_STATUS.replace("{identifier}", deviceId)
        val response = HttpClient.post(
            endpoint,
            mapOf(
                "phone_number" to phoneNumber,
                "message" to message,
                "status" to status,
                "error" to (error ?: ""),
                "request_id" to (requestId ?: ""),
                "timestamp" to timestamp.toString()
            ),
            deviceToken
        )
        
        return if (response.success) {
            HttpClient.parseResponse<ApiWrapper<SmsStatusResponse>>(response)?.data?.let {
                updateQuota(it.quota)
                Result.success(it)
            } ?: Result.success(SmsStatusResponse(true))
        } else {
            Result.failure(Exception("Status update failed: ${response.code}"))
        }
    }

    /**
     * Submit a batch of status updates to the API.
     */
    suspend fun submitStatusBatch(updates: List<StatusUpdate>): Result<SmsStatusResponse> {
        val deviceToken = deviceService.getDeviceToken()
            ?: return Result.failure(Exception("Device not registered"))
        
        val deviceId = deviceService.getDeviceIdentifier()
            ?: return Result.failure(Exception("Device identifier not found"))
        
        val jsonBody = HttpClient.json.encodeToString(StatusBatchRequest(updates))
        val endpoint = ApiConfig.Endpoints.SMS_STATUS_BATCH.replace("{identifier}", deviceId)
        
        val response = HttpClient.postJson(
            endpoint,
            jsonBody,
            deviceToken
        )
        
        return if (response.success) {
            HttpClient.parseResponse<ApiWrapper<SmsStatusResponse>>(response)?.data?.let {
                updateQuota(it.quota)
                Result.success(it)
            } ?: Result.success(SmsStatusResponse(true))
        } else {
            Result.failure(Exception("Batch status update failed: ${response.code}"))
        }
    }

    /**
     * Fetch current subscription quota.
     */
    suspend fun getQuota(): Result<QuotaInfo> {
        val deviceToken = deviceService.getDeviceToken()
            ?: return Result.failure(Exception("Device not registered"))
        
        val deviceId = deviceService.getDeviceIdentifier()
            ?: return Result.failure(Exception("Device identifier not found"))

        val endpoint = ApiConfig.Endpoints.SMS_QUOTA.replace("{identifier}", deviceId)
        val response = HttpClient.get(endpoint, deviceToken)
        
        return if (response.success) {
            HttpClient.parseResponse<ApiWrapper<QuotaInfo>>(response)?.data?.let {
                updateQuota(it)
                Result.success(it)
            } ?: Result.failure(Exception("Failed to parse quota response"))
        } else {
            Result.failure(Exception("Failed to fetch quota: ${response.code}"))
        }
    }
}
