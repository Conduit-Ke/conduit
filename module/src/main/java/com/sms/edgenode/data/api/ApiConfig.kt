package com.sms.edgenode.data.api

/**
 * API configuration for the SMS Edge Node.
 * V2 API — Rust/Axum backend.
 */
object ApiConfig {
    // Base URL for the API — no version prefix; each endpoint carries its own path.
    // Local: http://192.168.2.102:8080  (or use 10.0.2.2 if in emulator)
    // Production: https://api.conduit.threelions.co.ke
    const val BASE_URL = "http://192.168.2.106:3000"
    
    // Connection timeouts
    const val CONNECT_TIMEOUT_MS = 30_000
    const val READ_TIMEOUT_MS = 30_000
    
    // Endpoints — V2 API
    object Endpoints {
        // ── Mobile Auth (unversioned, top-level) ──
        const val LOGIN = "/mobile/login"
        const val REGISTER = "/mobile/register"
        const val VERIFY_OTP = "/mobile/verify_otp"
        const val REFRESH = "/mobile/refresh"
        const val LOGOUT = "/mobile/logout"
        const val ME = "/me"
        
        // ── Provisioning (V2 scoped) ──
        const val PROVISIONING = "/v2/provisioning/"
        
        // ── Network / Device Management (V2 scoped) ──
        const val DEVICE_LIST = "/v2/network/devices"
        const val DEVICE_CREATE = "/v2/network/devices"
        const val DEVICE_GET = "/v2/network/devices/{public_id}"
        const val DEVICE_UPDATE = "/v2/network/devices/{public_id}"
        
        // ── System / Device Operations (V2 scoped) ──
        const val DEVICE_PING = "/v2/system/devices/ping"
        const val DEVICE_STATUS_UPDATE = "/v2/system/devices/status"
        
        // ── SMS Ingress (V2 scoped) ──
        const val SMS_INGRESS = "/v2/sms/ingress"
        const val SMS_INGRESS_BATCH = "/v2/sms/ingress/batch"
        
        // ── SMS Status Reporting (V2 scoped) ──
        const val SMS_STATUS = "/v2/sms/status"
        const val SMS_STATUS_BATCH = "/v2/sms/status/batch"
    }
}
