package com.sms.edgenode.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.edgenode.data.api.AuthService
import com.sms.edgenode.ui.screens.LoginStep
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

import com.sms.edgenode.data.api.DeviceService
import android.os.Build

class LoginViewModel(private val authService: AuthService, private val deviceService: DeviceService) : ViewModel() {

    var currentStep by mutableStateOf(LoginStep.EMAIL)
        private set

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var otp by mutableStateOf("")
    
    var isLoading by mutableStateOf(false)
        private set

    var userId by mutableStateOf<Int?>(null)
        private set

    private val _events = MutableSharedFlow<LoginEvent>()
    val events = _events.asSharedFlow()

    sealed class LoginEvent {
        data class ShowNotification(val message: String, val type: NotificationType) : LoginEvent()
        object LoginSuccess : LoginEvent()
        object TriggerShake : LoginEvent()
    }

    enum class NotificationType { SUCCESS, ERROR }

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            viewModelScope.launch {
                _events.emit(LoginEvent.TriggerShake)
                _events.emit(LoginEvent.ShowNotification("Email and password required", NotificationType.ERROR))
            }
            return
        }

        viewModelScope.launch {
            isLoading = true
            val result = authService.login(email, password)
            result.onSuccess { response ->
                if (response.otp_required) {
                    userId = response.user_id
                    currentStep = LoginStep.OTP
                    _events.emit(LoginEvent.ShowNotification(response.message ?: "OTP sent", NotificationType.SUCCESS))
                } else {
                    val profileResult = authService.fetchUserProfile()
                    if (profileResult.isFailure) {
                        _events.emit(LoginEvent.TriggerShake)
                        _events.emit(LoginEvent.ShowNotification("Failed to fetch profile", NotificationType.ERROR))
                        return@onSuccess
                    }
                    
                    val orgId = authService.getOrganizationId()
                    if (orgId == -1) {
                        _events.emit(LoginEvent.TriggerShake)
                        _events.emit(LoginEvent.ShowNotification("User has no organization", NotificationType.ERROR))
                        return@onSuccess
                    }
                    
                    val deviceName = Build.MODEL ?: "Android Edge Node"
                    val regResult = deviceService.register(deviceName, orgId)
                    
                    if (regResult.isSuccess) {
                        _events.emit(LoginEvent.LoginSuccess)
                    } else {
                        _events.emit(LoginEvent.TriggerShake)
                        _events.emit(LoginEvent.ShowNotification("Failed to register device", NotificationType.ERROR))
                    }
                }
            }.onFailure { error ->
                _events.emit(LoginEvent.TriggerShake)
                _events.emit(LoginEvent.ShowNotification(error.message ?: "Login failed", NotificationType.ERROR))
            }
            isLoading = false
        }
    }

    fun verifyOtp() {
        val uid = userId
        if (uid != null && otp.length == 6) {
            viewModelScope.launch {
                isLoading = true
                val result = authService.verifyOtp(uid, otp)
                result.onSuccess {
                    val profileResult = authService.fetchUserProfile()
                    if (profileResult.isFailure) {
                        _events.emit(LoginEvent.TriggerShake)
                        _events.emit(LoginEvent.ShowNotification("Failed to fetch profile", NotificationType.ERROR))
                        return@onSuccess
                    }
                    
                    val orgId = authService.getOrganizationId()
                    if (orgId == -1) {
                        _events.emit(LoginEvent.TriggerShake)
                        _events.emit(LoginEvent.ShowNotification("User has no organization", NotificationType.ERROR))
                        return@onSuccess
                    }
                    
                    val deviceName = Build.MODEL ?: "Android Edge Node"
                    val regResult = deviceService.register(deviceName, orgId)
                    
                    if (regResult.isSuccess) {
                        _events.emit(LoginEvent.LoginSuccess)
                    } else {
                        _events.emit(LoginEvent.TriggerShake)
                        _events.emit(LoginEvent.ShowNotification("Failed to register device", NotificationType.ERROR))
                    }
                }.onFailure { error ->
                    _events.emit(LoginEvent.TriggerShake)
                    _events.emit(LoginEvent.ShowNotification(error.message ?: "Invalid OTP", NotificationType.ERROR))
                }
                isLoading = false
            }
        } else {
            viewModelScope.launch {
                _events.emit(LoginEvent.TriggerShake)
                _events.emit(LoginEvent.ShowNotification("Enter valid OTP", NotificationType.ERROR))
            }
        }
    }
}
