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

    /**
     * In V2, we track the session_id (String/UUID) instead of user_id (Int).
     */
    var sessionId by mutableStateOf<String?>(null)
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
                // V2 API always requires OTP for mobile login
                if (response.otp_required && response.session_id != null) {
                    sessionId = response.session_id
                    currentStep = LoginStep.OTP
                    _events.emit(LoginEvent.ShowNotification(response.message ?: "Verification code sent to your email", NotificationType.SUCCESS))
                } else {
                    // This branch should theoretically not be hit in V2, but we'll handle it for robustness
                    handlePostAuthFlow()
                }
            }.onFailure { error ->
                _events.emit(LoginEvent.TriggerShake)
                _events.emit(LoginEvent.ShowNotification(error.message ?: "Login failed", NotificationType.ERROR))
            }
            isLoading = false
        }
    }

    fun verifyOtp() {
        val sid = sessionId
        if (sid != null && otp.length == 6) {
            viewModelScope.launch {
                isLoading = true
                val result = authService.verifyOtp(sid, otp)
                result.onSuccess {
                    handlePostAuthFlow()
                }.onFailure { error ->
                    _events.emit(LoginEvent.TriggerShake)
                    _events.emit(LoginEvent.ShowNotification(error.message ?: "Invalid code", NotificationType.ERROR))
                }
                isLoading = false
            }
        } else {
            viewModelScope.launch {
                _events.emit(LoginEvent.TriggerShake)
                _events.emit(LoginEvent.ShowNotification("Enter valid 6-digit code", NotificationType.ERROR))
            }
        }
    }

    /**
     * Handles the flow after successful authentication (OTP verified).
     * Fetches user profile and registers the device.
     */
    private suspend fun handlePostAuthFlow() {
        // 1. Fetch user profile to ensure we have org context and fresh metadata
        val profileResult = authService.fetchUserProfile()
        if (profileResult.isFailure) {
            _events.emit(LoginEvent.TriggerShake)
            _events.emit(LoginEvent.ShowNotification("Failed to fetch user profile", NotificationType.ERROR))
            return
        }
        
        val orgId = authService.getOrganizationId()
        if (orgId == -1L) {
            _events.emit(LoginEvent.TriggerShake)
            _events.emit(LoginEvent.ShowNotification("User has no associated organization", NotificationType.ERROR))
            return
        }
        
        // 2. Register this device as an Edge Node
        // V2 uses the hardware ANDROID_ID as device_id and JWT for auth.
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val regResult = deviceService.register(deviceName, orgId)
        
        if (regResult.isSuccess) {
            _events.emit(LoginEvent.LoginSuccess)
        } else {
            _events.emit(LoginEvent.TriggerShake)
            val error = regResult.exceptionOrNull()?.message ?: "Device registration failed"
            _events.emit(LoginEvent.ShowNotification(error, NotificationType.ERROR))
        }
    }
}
