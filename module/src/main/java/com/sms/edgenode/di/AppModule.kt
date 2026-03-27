package com.sms.edgenode.di

import com.sms.edgenode.ui.viewmodel.*
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import com.sms.edgenode.data.db.EdgeNodeDatabase
import androidx.room.Room
import androidx.security.crypto.MasterKey
import androidx.security.crypto.EncryptedSharedPreferences
import com.sms.edgenode.data.api.AuthService
import com.sms.edgenode.data.api.DeviceService
import com.sms.edgenode.data.api.SmsApiService
import com.sms.edgenode.sender.SmsManager
import com.sms.edgenode.sender.SimHealthManager
import com.sms.edgenode.sender.UssdManager
import com.sms.edgenode.data.db.AuditLogManager
import com.sms.edgenode.observability.DeviceHealthMonitor

val appModule = module {
    // Database
    single {
        val masterKey = MasterKey.Builder(androidContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        val prefs = EncryptedSharedPreferences.create(
            androidContext(),
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
        
        val dbKey = java.security.MessageDigest.getInstance("SHA-256")
            .digest(passphrase.toByteArray())

        Room.databaseBuilder(
            androidContext(),
            EdgeNodeDatabase::class.java,
            "edge_node_db"
        )
            .openHelperFactory(net.sqlcipher.database.SupportFactory(dbKey))
            .addMigrations(com.sms.edgenode.data.db.MIGRATION_1_2, com.sms.edgenode.data.db.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    // Services
    single { AuthService(androidContext()) }
    single { DeviceService(androidContext(), get()) }
    single { SmsApiService(get()) }
    single { DeviceHealthMonitor(androidContext()) }
    single { SmsManager(androidContext()) }
    single { UssdManager(androidContext()) }
    single { SimHealthManager(androidContext()) }
    single { com.sms.edgenode.observability.NotificationHelper(androidContext()) }
    
    // Daos
    single { get<EdgeNodeDatabase>().auditLogDao() }
    single { get<EdgeNodeDatabase>().smsDao() }
    
    // Managers
    single { AuditLogManager(get()) }
    single { UssdManager(get()) }

    // ViewModels
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get(), androidContext(), get()) }
    viewModel { QueueViewModel(get()) }
    viewModel { LogsViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
}
