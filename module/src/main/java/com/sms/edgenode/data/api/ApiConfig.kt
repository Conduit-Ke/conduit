package com.sms.edgenode.data.api

/**
 * API configuration for the SMS Edge Node.
 */
object ApiConfig {
    // Base URL for the API - change for production
    var baseUrl: String = "http://192.168.100.4:3000/api"
    
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
        
        const val DEVICE_REGISTER = "/devices/register"
        const val DEVICE_STATUS = "/devices/{identifier}/status"
        
        const val SMS_RECEIVE = "/sms"
        const val SMS_STATUS = "/sms/status"
    }
}
