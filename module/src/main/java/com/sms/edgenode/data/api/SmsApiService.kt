package com.sms.edgenode.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * SMS API service for submitting received SMS and status updates.
 * Data is encrypted before sending.
 */
class SmsApiService(private val deviceService: DeviceService) {
    
    @Serializable
    data class SmsSubmitResponse(
        val success: Boolean,
        val message_id: String? = null,
        val message: String? = null,
        val error: String? = null,
        val duplicate: Boolean = false
    )
    
    @Serializable
    data class SmsStatusResponse(
        val success: Boolean,
        val message: String? = null
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
        val success: Boolean,
        val results: List<SmsSubmitResponse>? = null,
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
        
        // Encrypt sensitive data
        val encryptedSender = EncryptionHelper.encrypt(sender, organizationId)
        val encryptedMessage = EncryptionHelper.encrypt(message, organizationId)
        
        val response = HttpClient.post(
            ApiConfig.Endpoints.SMS_RECEIVE,
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
            HttpClient.parseResponse<SmsSubmitResponse>(response)?.let {
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

        val encryptedBatch = batch.map { item ->
            item.copy(
                sender = EncryptionHelper.encrypt(item.sender, organizationId),
                message = EncryptionHelper.encrypt(item.message, organizationId)
            )
        }

        val jsonBody = HttpClient.json.encodeToString(SmsBatchRequest(encryptedBatch))
        val response = HttpClient.postJson(
            "${ApiConfig.Endpoints.SMS_RECEIVE}-batch",
            jsonBody,
            deviceToken
        )

        return if (response.success) {
            HttpClient.parseResponse<SmsBatchResponse>(response)?.let {
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
        
        val response = HttpClient.post(
            ApiConfig.Endpoints.SMS_STATUS,
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
            HttpClient.parseResponse<SmsStatusResponse>(response)?.let {
                Result.success(it)
            } ?: Result.success(SmsStatusResponse(true))
        } else {
            Result.failure(Exception("Status update failed: ${response.code}"))
        }
    }
}
