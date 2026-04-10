package com.sms.edgenode.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.edgenode.data.db.EdgeNodeDatabase
import com.sms.edgenode.data.db.AuditLogEntity
import com.sms.edgenode.service.RoutingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val db: EdgeNodeDatabase,
    private val context: Context,
    private val authService: com.sms.edgenode.data.api.AuthService
) : ViewModel() {

    var isServiceRunning by mutableStateOf(false)
        private set



    val queueSize = db.smsDao().observeQueue()
        .map { it.count { msg -> msg.status == com.sms.edgenode.data.db.SmsStatus.RECEIVED || 
                                 msg.status == com.sms.edgenode.data.db.SmsStatus.QUEUED ||
                                 msg.status == com.sms.edgenode.data.db.SmsStatus.SENDING ||
                                 msg.status == com.sms.edgenode.data.db.SmsStatus.SUBMITTED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val deliveredCount = db.smsDao().observeQueue()
        .map { it.count { msg -> msg.status == com.sms.edgenode.data.db.SmsStatus.DELIVERED ||
                                 msg.status == com.sms.edgenode.data.db.SmsStatus.SENT } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount = db.smsDao().observeQueue()
        .map { it.count { msg -> msg.status == com.sms.edgenode.data.db.SmsStatus.FAILED || 
                                 msg.status == com.sms.edgenode.data.db.SmsStatus.EXPIRED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalProcessed = combine(deliveredCount, failedCount) { delivered, failed ->
        delivered + failed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val successRate = combine(deliveredCount, totalProcessed) { delivered, total ->
        if (total == 0) 0f else (delivered.toFloat() / total.toFloat())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    init {
        viewModelScope.launch {
            while (true) {
                val isRunning = checkServiceRunning(context)
                // If the user manually enabled it but checkServiceRunning is failing (common on Android 14),
                // we treat it as running if the auth flag says so and it's not explicitly stopped.
                isServiceRunning = isRunning || (!authService.isServiceDisabledByUser())
                
                // Auto-start if not running but not disabled by user
                if (!isRunning && !authService.isServiceDisabledByUser() && authService.isLoggedIn()) {
                    android.util.Log.i("HomeViewModel", "Auto-starting RoutingService via init loop")
                    RoutingService.start(context)
                }
                
                delay(5000)
            }
        }
    }

    private fun checkServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return try {
            @Suppress("DEPRECATION")
            manager.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == RoutingService::class.java.name
            }
        } catch (e: Exception) {
            false
        }
    }

    fun toggleService() {
        if (isServiceRunning) {
            authService.setServiceDisabledByUser(true)
            RoutingService.stop(context)
        } else {
            authService.setServiceDisabledByUser(false)
            RoutingService.start(context)
        }
        isServiceRunning = !isServiceRunning
    }
}
