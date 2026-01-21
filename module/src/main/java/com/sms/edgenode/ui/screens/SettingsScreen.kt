package com.sms.edgenode.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import com.sms.edgenode.ui.MainActivity
import com.sms.edgenode.ui.LocalTopNotificationState
import com.sms.edgenode.ui.theme.ThemeState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit
) {
    var isDarkTheme by ThemeState.isDarkTheme
    var showLogoutDialog by remember { mutableStateOf(false) }
    val topNotification = LocalTopNotificationState.current
    
    // Track lifecycle events to refresh permissions
    var permissionRefreshTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionRefreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Define permission groups
    val permissionGroups = remember {
        listOf(
            PermissionGroup(
                label = "SMS Access",
                permissions = listOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_SMS
                ),
                description = "Required to route messages",
                icon = Icons.Outlined.Sms
            ),
            PermissionGroup(
                label = "Telephony",
                permissions = listOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.CALL_PHONE
                ),
                description = "SIM status & USSD balance",
                icon = Icons.Outlined.Phone
            ),
            PermissionGroup(
                label = "Notifications",
                permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                description = "Status updates",
                icon = Icons.Outlined.Notifications
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Appearance Section
        SettingsSection(title = "APPEARANCE") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                "Dark Theme",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (isDarkTheme) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { isDarkTheme = it }
                    )
                }
            }
        }

        // Permissions Section
        SettingsSection(title = "PERMISSIONS") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    permissionGroups.forEachIndexed { _, group ->
                        key(group.label, permissionRefreshTrigger) {
                            PermissionGroupRow(
                                group = group,
                                refreshTrigger = permissionRefreshTrigger
                            )
                        }
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    
                    BatteryOptimizationRow(permissionRefreshTrigger)
                }
            }
        }

        // Advanced Routing Section
        val context = LocalContext.current
        val app = context.applicationContext as com.sms.edgenode.EdgeNodeApp
        var preferredSim by remember { mutableIntStateOf(app.authService.getPreferredSimSlot()) }
        val activeSims = remember { com.sms.edgenode.sender.SimDetector.getActiveSimSlots(context) }
        
        SettingsSection(title = "ROUTING") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SIM Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.SimCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Preferred SIM",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Used for outgoing messages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(-1, 0, 1).forEach { slot ->
                            val label = when (slot) {
                                -1 -> "Default"
                                0 -> "SIM 1"
                                1 -> "SIM 2"
                                else -> "SIM $slot"
                            }
                            FilterChip(
                                selected = preferredSim == slot,
                                onClick = { 
                                    preferredSim = slot
                                    app.authService.setPreferredSimSlot(slot)
                                },
                                label = { Text(label) },
                                enabled = slot == -1 || activeSims.contains(slot),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Smart Failover
                    var smartFailover by remember { mutableStateOf(app.authService.isSmartFailoverEnabled()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = if (smartFailover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    "Smart Failover",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Auto-switch SIM on failures",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = smartFailover,
                            onCheckedChange = { 
                                smartFailover = it
                                app.authService.setSmartFailoverEnabled(it)
                            }
                        )
                    }
                }
            }
        }

        // Operational Section
        SettingsSection(title = "OPERATIONAL") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Invisible Mode
                    var invisibleMode by remember { mutableStateOf(app.authService.isInvisibleModeEnabled()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (invisibleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    "Invisible Mode",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Humanized patterns to avoid blocking",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = invisibleMode,
                            onCheckedChange = { 
                                invisibleMode = it
                                app.authService.setInvisibleModeEnabled(it)
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // USSD Auto-Broker
                    var autoBroker by remember { mutableStateOf(app.authService.isUssdAutoCheckEnabled()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = null,
                                tint = if (autoBroker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    "USSD Auto-Broker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Autonomous credit management",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = autoBroker,
                            onCheckedChange = { 
                                autoBroker = it
                                app.authService.setUssdAutoCheckEnabled(it)
                            }
                        )
                    }

                    if (autoBroker) {
                        val airtimeCode = remember { mutableStateOf(app.authService.getUssdAirtimeCode()) }
                        val smsCode = remember { mutableStateOf(app.authService.getUssdSmsCode()) }
                        val topupCode = remember { mutableStateOf(app.authService.getUssdTopupCode()) }
                        var ussdResult by remember { mutableStateOf<String?>(null) }
                        var isCheckingAirtime by remember { mutableStateOf(false) }
                        var isCheckingSms by remember { mutableStateOf(false) }

                        OutlinedTextField(
                            value = airtimeCode.value,
                            onValueChange = { 
                                airtimeCode.value = it
                                app.authService.setUssdAirtimeCode(it)
                            },
                            label = { Text("Airtime Balance Command") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = smsCode.value,
                            onValueChange = { 
                                smsCode.value = it
                                app.authService.setUssdSmsCode(it)
                            },
                            label = { Text("SMS Balance Command") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = topupCode.value,
                            onValueChange = { 
                                topupCode.value = it
                                app.authService.setUssdTopupCode(it)
                            },
                            label = { Text("Top-up Format (*141*CARD#)") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Enterprise Refill & Bundle Logic
                        var autoBundle by remember { mutableStateOf(app.authService.isAutoBundleEnabled()) }
                        var refillThreshold by remember { mutableStateOf(app.authService.getRefillThreshold()) }
                        val bundlePurchaseCode = remember { mutableStateOf(app.authService.getUssdBundlePurchaseCode()) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Bundle Conversion", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Convert airtime to SMS on recharge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoBundle, onCheckedChange = { 
                                autoBundle = it
                                app.authService.setAutoBundleEnabled(it)
                            })
                        }

                        if (autoBundle) {
                            OutlinedTextField(
                                value = bundlePurchaseCode.value,
                                onValueChange = { 
                                    bundlePurchaseCode.value = it
                                    app.authService.setUssdBundlePurchaseCode(it)
                                },
                                label = { Text("Bundle Purchase Command") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Refill Threshold", style = MaterialTheme.typography.labelMedium)
                                Text("KES ${refillThreshold.toInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = refillThreshold.toFloat(),
                                onValueChange = { 
                                    val rounded = (Math.round(it / 5f) * 5).toDouble()
                                    refillThreshold = rounded
                                    app.authService.setRefillThreshold(rounded)
                                },
                                valueRange = 5f..100f,
                                steps = 18
                            )
                        }

                        val coroutineScope = rememberCoroutineScope()
                        var isRequestingRefill by remember { mutableStateOf(false) }
                        
                        OutlinedButton(
                            onClick = {
                                isRequestingRefill = true
                                app.authService.setLastRefillRequestTime(0L) // Reset cool-off for manual override
                                coroutineScope.launch {
                                    val result = app.deviceService.requestRefill(refillThreshold)
                                    if (result.isSuccess) {
                                        val msg = "Refill request sent to server."
                                        topNotification.showNotification(msg, Icons.Default.CheckCircle, Color.Green)
                                        ussdResult = msg
                                    } else {
                                        val error = result.exceptionOrNull()?.message ?: "Unknown Error"
                                        val msg = if (error.contains("402")) {
                                            "Error: Organization backend balance is insufficient."
                                        } else {
                                            "Refill failed: $error"
                                        }
                                        topNotification.showNotification(msg, Icons.Default.Error, Color.Red)
                                        ussdResult = msg
                                    }
                                    isRequestingRefill = false
                                }
                            },
                            enabled = !isRequestingRefill,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRequestingRefill) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Manual Refill Request")
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isCheckingAirtime = true
                                    coroutineScope.launch {
                                        val result = app.ussdManager.sendUssd(airtimeCode.value, preferredSim)
                                        val msg = "Airtime: " + result.fold({ it }, { it.message })
                                        topNotification.showNotification(
                                            msg, 
                                            if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                            if (result.isSuccess) Color.Cyan else Color.Red
                                        )
                                        ussdResult = msg
                                        isCheckingAirtime = false
                                    }
                                },
                                enabled = !isCheckingAirtime && !isCheckingSms,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                if (isCheckingAirtime) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text("Check Airtime", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    isCheckingSms = true
                                    coroutineScope.launch {
                                        val result = app.ussdManager.sendUssd(smsCode.value, preferredSim)
                                        val msg = "SMS: " + result.fold({ it }, { it.message })
                                        topNotification.showNotification(
                                            msg, 
                                            if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                            if (result.isSuccess) Color.Cyan else Color.Red
                                        )
                                        ussdResult = msg
                                        isCheckingSms = false
                                    }
                                },
                                enabled = !isCheckingAirtime && !isCheckingSms,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                if (isCheckingSms) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text("Check SMS", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Note: If the carrier returns balance via SMS, check the 'Logs' tab for a 'Carrier Update' entry.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (ussdResult != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    ussdResult!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(8.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // About Section
        SettingsSection(title = "ABOUT") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val orgId = app.authService.getOrganizationId()
                    val nodeId = android.provider.Settings.Secure.getString(
                        context.contentResolver, 
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "Unknown"

                    InfoRow(
                        icon = Icons.Outlined.Info,
                        label = "Version",
                        value = "1.0.0"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    InfoRow(
                        icon = Icons.Outlined.Build,
                        label = "Build Status",
                        value = "Operational"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    InfoRow(
                        icon = Icons.Outlined.CorporateFare,
                        label = "Organization ID",
                        value = if (orgId != -1) "#$orgId" else "None"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    InfoRow(
                        icon = Icons.Outlined.Dns,
                        label = "Node ID",
                        value = nodeId.take(12).uppercase()
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    InfoRow(
                        icon = Icons.Outlined.CloudQueue,
                        label = "Edge Server",
                        value = com.sms.edgenode.data.api.ApiConfig.baseUrl.substringBefore("/api").removePrefix("http://").removePrefix("https://")
                    )
                }
            }
        }

        // Session Section
        SettingsSection(title = "SESSION") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                "Logout",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "End current session",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { showLogoutDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Logout")
                    }
                }
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("Logout?") },
                text = { Text("You will need to authenticate again to manage the service.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            onLogout()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Logout")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        content()
    }
}

data class PermissionGroup(
    val label: String,
    val permissions: List<String>,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun PermissionGroupRow(
    group: PermissionGroup,
    refreshTrigger: Int
) {
    val context = LocalContext.current
    
    // Check if ALL permissions in the group are granted, reactively
    val isGranted = remember(group.permissions, refreshTrigger) {
        group.permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            group.icon,
            contentDescription = null,
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                group.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isGranted) "Granted" else group.description,
                style = MaterialTheme.typography.labelSmall,
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isGranted) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            FilledTonalButton(
                onClick = { launcher.launch(group.permissions.toTypedArray()) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("Grant", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BatteryOptimizationRow(refreshTrigger: Int) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    val isIgnoringOptimizations = remember(refreshTrigger) { 
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.BatteryChargingFull,
            contentDescription = null,
            tint = if (isIgnoringOptimizations) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Battery Optimization",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isIgnoringOptimizations) "Optimizations Ignored" else "Exempt app from battery limits",
                style = MaterialTheme.typography.labelSmall,
                color = if (isIgnoringOptimizations) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isIgnoringOptimizations) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            FilledTonalButton(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to general settings if direct intent fails
                        val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(intent)
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("Grant", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
