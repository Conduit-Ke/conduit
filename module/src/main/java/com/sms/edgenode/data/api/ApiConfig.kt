package com.sms.edgenode.data.api

/**
 * API configuration for the SMS Edge Node.
 */
object ApiConfig {
    // Base URL for the API - change for production
    // Local: http://192.168.2.102:8080/v1  (or use host.docker.internal if in emulator)
    // Production: https://18.153.158.84/api/v1/app
    var baseUrl: String = "http://192.168.2.102:8080/v1/app"
    
    // Connection timeouts
    const val CONNECT_TIMEOUT_MS = 30_000
    const val READ_TIMEOUT_MS = 30_000
    
    // Endpoints
    object Endpoints {
        const val LOGIN = "/auth/login"
        const val VERIFY_OTP = "/auth/verify-otp"
        const val RESEND_OTP = "/auth/resend-otp"
        const val REFRESH = "/auth/refresh"
        const val LOGOUT = "/auth/logout"
        const val ME = "/auth/me"
        const val PROVISIONING = "/auth/provisioning"
        
        const val DEVICE_REGISTER = "/devices/register"
        const val DEVICE_STATUS = "/devices/{identifier}/status"
        const val DEVICE_VERIFY = "/devices/{identifier}/verify"
        const val DEVICE_HEARTBEAT = "/devices/{identifier}/heartbeat"
        const val DEVICE_TELEMETRY = "/devices/{identifier}/telemetry"
        const val DEVICE_FCM_TOKEN = "/devices/{identifier}/fcm-token"
        const val DEVICE_METRICS = "/devices/{identifier}/metrics"
        const val DEVICE_REFILL = "/devices/{identifier}/refill"
        
        const val SMS_RECEIVE = "/devices/{identifier}/receive"
        const val SMS_BATCH = "/devices/{identifier}/receive-batch"
        const val SMS_STATUS = "/devices/{identifier}/status"
        const val SMS_STATUS_BATCH = "/devices/{identifier}/status-batch"
        const val SMS_QUOTA = "/devices/{identifier}/quota"
    }
}
