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
import androidx.security.crypto.MasterKey
import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.coroutines.*

class EdgeNodeApp : Application() {
    lateinit var authService: AuthService
    lateinit var deviceService: DeviceService
    lateinit var smsApiService: SmsApiService
    
    lateinit var database: EdgeNodeDatabase
    lateinit var smsManager: SmsManager
    lateinit var simHealthManager: SimHealthManager
    lateinit var auditLogManager: AuditLogManager
    lateinit var ussdManager: com.sms.edgenode.sender.UssdManager
    lateinit var healthMonitor: com.sms.edgenode.observability.DeviceHealthMonitor

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase manually since auto-init is disabled in Manifest
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("EdgeNodeApp", "Failed to initialize Firebase: ${e.message}")
        }
        
        // Initialize API Services
        authService = AuthService(applicationContext)
        deviceService = DeviceService(applicationContext, authService)
        smsApiService = SmsApiService(deviceService)
        healthMonitor = com.sms.edgenode.observability.DeviceHealthMonitor(applicationContext)
        
        // Initialize Security Services (Layered Security)
        authService.getControlSecurityKey()?.let { 
            com.sms.edgenode.security.PayloadEncryption.initialize(it)
        }
        
        // Wire HttpClient to auto-refresh tokens
        com.sms.edgenode.data.api.HttpClient.onUnauthorized = {
            android.util.Log.w("EdgeNodeApp", "Session expired, attempting background token refresh...")
            val result = authService.refreshToken()
            result.isSuccess
        }

        database = Room.databaseBuilder(
            applicationContext,
            EdgeNodeDatabase::class.java,
            "edge_node_db"
        )
            .openHelperFactory(net.sqlcipher.database.SupportFactory(getDatabasePassphrase()))
            .addMigrations(com.sms.edgenode.data.db.MIGRATION_1_2, com.sms.edgenode.data.db.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

        smsManager = SmsManager(applicationContext)
        simHealthManager = SimHealthManager(applicationContext)
        auditLogManager = AuditLogManager(database.auditLogDao())
        ussdManager = com.sms.edgenode.sender.UssdManager(applicationContext)
        
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

    private fun getDatabasePassphrase(): ByteArray {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        val prefs = EncryptedSharedPreferences.create(
            applicationContext,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        
        var passphrase = prefs.getString("db_passphrase", null)
        if (passphrase == null) {
            passphrase = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("db_passphrase", passphrase).apply()
        }
        
        // Elite derivation: 256-bit hash for the SQLCipher key
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(passphrase.toByteArray())
    }
}
