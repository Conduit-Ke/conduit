package com.sms.edgenode

import android.app.Application
import androidx.room.Room
import com.sms.edgenode.data.db.EdgeNodeDatabase
import com.sms.edgenode.sender.SmsManager
import com.sms.edgenode.sender.SimHealthManager
import com.sms.edgenode.data.db.AuditLogManager
import com.sms.edgenode.data.api.AuthService
import com.sms.edgenode.data.api.DeviceService
import com.sms.edgenode.data.api.SmsApiService
import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.coroutines.*
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EdgeNodeApp : Application(), KoinComponent {
    private val authService: AuthService by inject()
    private val deviceService: DeviceService by inject()
    private val smsApiService: SmsApiService by inject()
    private val healthMonitor: com.sms.edgenode.observability.DeviceHealthMonitor by inject()

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase manually since auto-init is disabled in Manifest
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
            com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = true
            android.util.Log.i("EdgeNodeApp", "Firebase initialized and auto-init enabled")
        } catch (e: Exception) {
            android.util.Log.e("EdgeNodeApp", "Failed to initialize Firebase: ${e.message}")
        }
        
        // Start Dependency Injection using Koin
        startKoin {
            androidLogger()
            androidContext(this@EdgeNodeApp)
            modules(com.sms.edgenode.di.appModule)
        }
        
        // Initialize Security Services (Layered Security)
        val securityKey = authService.getControlSecurityKey()
        if (securityKey != null) {
            com.sms.edgenode.security.PayloadEncryption.initialize(securityKey)
        } else {
            // Attempt to fetch fresh provisioning data if logged in but key is missing
            if (authService.isLoggedIn()) {
                MainScope().launch {
                    val result = authService.fetchProvisioning()
                    if (result.isSuccess) {
                        android.util.Log.i("EdgeNodeApp", "Provisioning data fetched successfully at startup")
                    } else {
                        android.util.Log.w("EdgeNodeApp", "Failed to fetch provisioning data at startup")
                    }
                }
            }
        }
        
        // Wire HttpClient to auto-refresh tokens
        com.sms.edgenode.data.api.HttpClient.onUnauthorized = {
            android.util.Log.w("EdgeNodeApp", "Session expired, attempting background token refresh...")
            val result = authService.refreshToken()
            result.isSuccess
        }
        com.sms.edgenode.data.api.HttpClient.tokenProvider = {
            authService.getAccessToken()
        }
        
        // Start Heartbeat
        healthMonitor.startHeartbeat { snapshot ->
            MainScope().launch {
                deviceService.sendHeartbeat(snapshot).onSuccess { response ->
                    response.quota?.let { smsApiService.updateQuota(it) }
                }
            }
        }

        com.sms.edgenode.observability.ServiceWatchdog.schedule(applicationContext)
    }

}
