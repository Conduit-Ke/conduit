package com.sms.edgenode.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.edgenode.data.api.AuthService
import com.sms.edgenode.data.api.DeviceService
import com.sms.edgenode.sender.UssdManager
import com.sms.edgenode.ui.theme.ThemeState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authService: AuthService,
    private val deviceService: DeviceService,
    private val ussdManager: UssdManager
) : ViewModel() {

    var isDarkTheme by mutableStateOf(ThemeState.isDarkTheme.value)
        private set
    var preferredSim by mutableIntStateOf(authService.getPreferredSimSlot())
    var smartFailover by mutableStateOf(authService.isSmartFailoverEnabled())
    var invisibleMode by mutableStateOf(authService.isInvisibleModeEnabled())
    var autoBroker by mutableStateOf(authService.isUssdAutoCheckEnabled())
    
    var airtimeCode by mutableStateOf(authService.getUssdAirtimeCode())
    var smsCode by mutableStateOf(authService.getUssdSmsCode())
    var topupCode by mutableStateOf(authService.getUssdTopupCode())
    
    var autoBundle by mutableStateOf(authService.isAutoBundleEnabled())
    var bundlePurchaseCode by mutableStateOf(authService.getUssdBundlePurchaseCode())
    var refillThreshold by mutableDoubleStateOf(authService.getRefillThreshold())
    
    var isRequestingRefill by mutableStateOf(false)
    var isCheckingAirtime by mutableStateOf(false)
    var isCheckingSms by mutableStateOf(false)

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    sealed class SettingsEvent {
        data class ShowNotification(val message: String, val type: NotificationType) : SettingsEvent()
    }

    enum class NotificationType { SUCCESS, ERROR, INFO }

    fun updateTheme(darkTheme: Boolean?) {
        isDarkTheme = darkTheme
        ThemeState.isDarkTheme.value = darkTheme
    }

    fun updatePreferredSim(slot: Int) {
        preferredSim = slot
        authService.setPreferredSimSlot(slot)
    }

    fun updateSmartFailover(enabled: Boolean) {
        smartFailover = enabled
        authService.setSmartFailoverEnabled(enabled)
    }

    fun updateInvisibleMode(enabled: Boolean) {
        invisibleMode = enabled
        authService.setInvisibleModeEnabled(enabled)
    }

    fun updateAutoBroker(enabled: Boolean) {
        autoBroker = enabled
        authService.setUssdAutoCheckEnabled(enabled)
    }

    fun updateAirtimeCode(code: String) {
        airtimeCode = code
        authService.setUssdAirtimeCode(code)
    }

    fun updateSmsCode(code: String) {
        smsCode = code
        authService.setUssdSmsCode(code)
    }

    fun updateTopupCode(code: String) {
        topupCode = code
        authService.setUssdTopupCode(code)
    }

    fun updateAutoBundle(enabled: Boolean) {
        autoBundle = enabled
        authService.setAutoBundleEnabled(enabled)
    }

    fun updateBundlePurchaseCode(code: String) {
        bundlePurchaseCode = code
        authService.setUssdBundlePurchaseCode(code)
    }

    fun updateRefillThreshold(threshold: Double) {
        val rounded = (Math.round(threshold / 5f) * 5).toDouble()
        refillThreshold = rounded
        authService.setRefillThreshold(rounded)
    }

    fun requestRefill() {
        viewModelScope.launch {
            // V2 refactor: Refill requests are handled via the dashboard in the new event-driven architecture.
            // This feature is currently deprecated in the mobile app.
            _events.emit(SettingsEvent.ShowNotification("Please manage refill requests via the Conduit dashboard.", NotificationType.INFO))
        }
    }

    fun checkAirtime() {
        viewModelScope.launch {
            isCheckingAirtime = true
            val result = ussdManager.sendUssd(airtimeCode, preferredSim)
            val msg = "Airtime: " + result.fold({ it }, { it.message })
            _events.emit(SettingsEvent.ShowNotification(msg, if (result.isSuccess) NotificationType.INFO else NotificationType.ERROR))
            isCheckingAirtime = false
        }
    }

    fun checkSms() {
        viewModelScope.launch {
            isCheckingSms = true
            val result = ussdManager.sendUssd(smsCode, preferredSim)
            val msg = "SMS: " + result.fold({ it }, { it.message })
            _events.emit(SettingsEvent.ShowNotification(msg, if (result.isSuccess) NotificationType.INFO else NotificationType.ERROR))
            isCheckingSms = false
        }
    }
}
