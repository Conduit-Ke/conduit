package com.sms.edgenode.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SMS API service for submitting received SMS (ingress) and delivery status reports.
 * V2 API — Rust/Axum backend.
 */
class SmsApiService(private val authService: AuthService) {
    
    // Quota info is handled via a separate billing/quota service in V2, 
    // but we'll keep the state here for UI compatibility if needed.
    private val _quotaInfo = MutableStateFlow<QuotaInfo?>(null)
    val quotaInfo: StateFlow<QuotaInfo?> = _quotaInfo.asStateFlow()

    fun updateQuota(newQuota: QuotaInfo?) {
        newQuota?.let { _quotaInfo.value = it }
    }
    
    @Serializable
    data class QuotaInfo(
        val received_count: Int = 0,
        val sent_count: Int = 0,
        val limit: Int = -1
    )

    // ── V2 Request Models (aligned with apiV2/crates/api/src/models/sms/dto.rs) ──

    @Serializable
    data class SmsIngressItem(
        val sender: String,
        val message: String,
        val message_id: String,
        val sent_timestamp: Long,
        val destination: String? = null
    )

    @Serializable
    data class SmsIngressBatchRequest(
        val batch: List<SmsIngressItem>
    )

    @Serializable
    data class SmsStatusReportItem(
        val egress_id: Long,
        val status: String, // e.g. "delivered", "failed"
        val error_message: String? = null,
        val timestamp: Long
    )

    @Serializable
    data class SmsStatusReportBatchRequest(
        val reports: List<SmsStatusReportItem>
    )

    // ── V2 Response Models ──

    @Serializable
    data class IngressResponse(
        val accepted: Boolean = true,
        val message_id: String? = null,
        val duplicate: Boolean = false
    )

    @Serializable
    data class StatusReportResponse(
        val acknowledged: Boolean = true
    )
    
    /**
     * Submit received SMS to API (ingress).
     */
    suspend fun submitSms(
        sender: String,
        message: String,
        messageId: String,
        destination: String? = null,
        sentTimestamp: Long
    ): Result<IngressResponse> {
        val token = authService.getAccessToken()
            ?: return Result.failure(Exception("Not authenticated"))
        
        // In V2, we don't encrypt ingress data at the application layer yet, 
        // as the server needs to read the sender and message for routing/keywords.
        // It's already protected by HTTPS and JWT.
        val request = SmsIngressItem(
            sender = sender,
            message = message,
            message_id = messageId,
            sent_timestamp = sentTimestamp,
            destination = destination
        )

        val jsonBody = HttpClient.json.encodeToString(request)
        val response = HttpClient.postJson(
            ApiConfig.Endpoints.SMS_INGRESS,
            jsonBody,
            token
        )
        
        return if (response.success) {
            HttpClient.parseV2Response<IngressResponse>(response)?.let {
                Result.success(it)
            } ?: Result.success(IngressResponse(true))
        } else {
            Result.failure(Exception("SMS ingress failed: ${response.code}"))
        }
    }

    /**
     * Submit a batch of received SMS to API (ingress batch).
     */
    suspend fun submitBatch(
        batch: List<SmsIngressItem>
    ): Result<Boolean> {
        val token = authService.getAccessToken()
            ?: return Result.failure(Exception("Not authenticated"))
        
        val encryptedBatch = batch.map { item ->
            item.copy(
                sender = com.sms.edgenode.security.PayloadEncryption.encrypt(item.sender) ?: item.sender,
                message = com.sms.edgenode.security.PayloadEncryption.encrypt(item.message) ?: item.message
            )
        }

        val jsonBody = HttpClient.json.encodeToString(SmsIngressBatchRequest(encryptedBatch))
        val response = HttpClient.postJson(
            ApiConfig.Endpoints.SMS_INGRESS_BATCH,
            jsonBody,
            token
        )

        return if (response.success) {
            Result.success(true)
        } else {
            Result.failure(Exception("SMS ingress batch failed: ${response.code}"))
        }
    }
    
    /**
     * Submit SMS delivery status report (for outgoing SMS).
     */
    suspend fun submitStatus(
        egressId: Long,
        status: String,
        errorMessage: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Result<StatusReportResponse> {
        val token = authService.getAccessToken()
            ?: return Result.failure(Exception("Not authenticated"))
        
        val report = SmsStatusReportItem(
            egress_id = egressId,
            status = status,
            error_message = errorMessage,
            timestamp = timestamp
        )

        val jsonBody = HttpClient.json.encodeToString(report)
        val response = HttpClient.postJson(
            ApiConfig.Endpoints.SMS_STATUS,
            jsonBody,
            token
        )
        
        return if (response.success) {
            HttpClient.parseV2Response<StatusReportResponse>(response)?.let {
                Result.success(it)
            } ?: Result.success(StatusReportResponse(true))
        } else {
            Result.failure(Exception("Status report failed: ${response.code}"))
        }
    }

    /**
     * Submit a batch of delivery status reports.
     */
    suspend fun submitStatusBatch(reports: List<SmsStatusReportItem>): Result<Boolean> {
        val token = authService.getAccessToken()
            ?: return Result.failure(Exception("Not authenticated"))
        
        val jsonBody = HttpClient.json.encodeToString(SmsStatusReportBatchRequest(reports))
        val response = HttpClient.postJson(
            ApiConfig.Endpoints.SMS_STATUS_BATCH,
            jsonBody,
            token
        )
        
        return if (response.success) {
            Result.success(true)
        } else {
            Result.failure(Exception("Batch status report failed: ${response.code}"))
        }
    }

    // ── V1 Backward Compatibility (Legacy field mapping) ──

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

    /**
     * Legacy submitStatusBatch for RoutingService transition.
     * Maps StatusUpdate list to SmsStatusReportItem list.
     */
    suspend fun submitStatusBatchLegacy(updates: List<StatusUpdate>): Result<Boolean> {
        val reports = updates.map {
            SmsStatusReportItem(
                egress_id = it.request_id?.toLongOrNull() ?: 0L,
                status = it.status.lowercase(),
                error_message = it.error,
                timestamp = it.timestamp
            )
        }
        return submitStatusBatch(reports)
    }
}
