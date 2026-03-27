package com.sms.edgenode.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sms.edgenode.ui.LocalTopNotificationState
import com.sms.edgenode.ui.theme.DesignTokens
import com.sms.edgenode.ui.viewmodel.SettingsViewModel
import com.sms.edgenode.ui.components.bounceClick
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val topNotification = LocalTopNotificationState.current
    val context = LocalContext.current
    
    // Resolve colors in composable scope
    val successColor = Color(0xFF4CAF50)
    val errorColor = Color(0xFFF44336)
    val infoColor = MaterialTheme.colorScheme.primary

    // Handle ViewModel Events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsViewModel.SettingsEvent.ShowNotification -> {
                    val (icon, color) = when (event.type) {
                        SettingsViewModel.NotificationType.SUCCESS -> Icons.Default.CheckCircle to successColor
                        SettingsViewModel.NotificationType.ERROR -> Icons.Default.Error to errorColor
                        SettingsViewModel.NotificationType.INFO -> Icons.Default.Info to infoColor
                    }
                    topNotification.showNotification(event.message, icon, color)
                }
            }
        }
    }
    
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
            .padding(all = DesignTokens.Spacing4),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing4)
    ) {
        // Appearance Section
        SettingsSection(title = "APPEARANCE") {
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    "Theme",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    when (viewModel.isDarkTheme) {
                                        true -> "Dark"
                                        false -> "Light"
                                        null -> "System"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple(null, "System", Icons.Default.Brightness6),
                            Triple(false, "Light", Icons.Default.LightMode),
                            Triple(true, "Dark", Icons.Default.DarkMode)
                        ).forEach { (value, label, icon) ->
                            FilterChip(
                                selected = viewModel.isDarkTheme == value,
                                onClick = { viewModel.updateTheme(value) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(icon, null, modifier = Modifier.size(14.dp)) },
                                shape = DesignTokens.CardShape,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Permissions Section
        SettingsSection(title = "PERMISSIONS") {
            SettingsCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    permissionGroups.forEachIndexed { index, group ->
                        key(group.label, permissionRefreshTrigger) {
                            PermissionGroupRow(
                                group = group,
                                refreshTrigger = permissionRefreshTrigger
                            )
                        }
                        if (index < permissionGroups.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
                    BatteryOptimizationRow(permissionRefreshTrigger)
                }
            }
        }

        // Routing Section
        val activeSims = remember { com.sms.edgenode.sender.SimDetector.getActiveSimSlots(context) }
        SettingsSection(title = "ROUTING") {
            SettingsCard {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.SimCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
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
                                selected = viewModel.preferredSim == slot,
                                onClick = { viewModel.updatePreferredSim(slot) },
                                label = { Text(label) },
                                enabled = slot == -1 || activeSims.contains(slot),
                                modifier = Modifier.weight(1f),
                                shape = DesignTokens.CardShape
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
                                tint = if (viewModel.smartFailover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
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
                            checked = viewModel.smartFailover,
                            onCheckedChange = { viewModel.updateSmartFailover(it) }
                        )
                    }
                }
            }
        }

        // Operational Section
        SettingsSection(title = "OPERATIONAL") {
            SettingsCard {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Invisible Mode
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
                                tint = if (viewModel.invisibleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
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
                            checked = viewModel.invisibleMode,
                            onCheckedChange = { viewModel.updateInvisibleMode(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // USSD Auto-Broker
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
                                tint = if (viewModel.autoBroker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
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
                            checked = viewModel.autoBroker,
                            onCheckedChange = { viewModel.updateAutoBroker(it) }
                        )
                    }

                    if (viewModel.autoBroker) {
                        OutlinedTextField(
                            value = viewModel.airtimeCode,
                            onValueChange = { viewModel.updateAirtimeCode(it) },
                            label = { Text("Airtime Balance Command") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            shape = DesignTokens.CardShape
                        )

                        OutlinedTextField(
                            value = viewModel.smsCode,
                            onValueChange = { viewModel.updateSmsCode(it) },
                            label = { Text("SMS Balance Command") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            shape = DesignTokens.CardShape
                        )

                        OutlinedTextField(
                            value = viewModel.topupCode,
                            onValueChange = { viewModel.updateTopupCode(it) },
                            label = { Text("Top-up Format (*141*CARD#)") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            shape = DesignTokens.CardShape
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Bundle Conversion", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Convert airtime to SMS on recharge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = viewModel.autoBundle, onCheckedChange = { viewModel.updateAutoBundle(it) })
                        }

                        if (viewModel.autoBundle) {
                            OutlinedTextField(
                                value = viewModel.bundlePurchaseCode,
                                onValueChange = { viewModel.updateBundlePurchaseCode(it) },
                                label = { Text("Bundle Purchase Command") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                shape = DesignTokens.CardShape
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Refill Threshold", style = MaterialTheme.typography.labelMedium)
                                Text("KES ${viewModel.refillThreshold.toInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = viewModel.refillThreshold.toFloat(),
                                onValueChange = { viewModel.updateRefillThreshold(it.toDouble()) },
                                valueRange = 5f..100f,
                                steps = 18
                            )
                        }
                        
                        OutlinedButton(
                            onClick = { viewModel.requestRefill() },
                            enabled = !viewModel.isRequestingRefill,
                            modifier = Modifier.fillMaxWidth(),
                            shape = DesignTokens.CardShape
                        ) {
                            if (viewModel.isRequestingRefill) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Manual Refill Request")
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.checkAirtime() },
                                enabled = !viewModel.isCheckingAirtime && !viewModel.isCheckingSms,
                                modifier = Modifier.weight(1f),
                                shape = DesignTokens.CardShape
                            ) {
                                if (viewModel.isCheckingAirtime) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text("Check Airtime")
                            }

                            Button(
                                onClick = { viewModel.checkSms() },
                                enabled = !viewModel.isCheckingAirtime && !viewModel.isCheckingSms,
                                modifier = Modifier.weight(1f),
                                shape = DesignTokens.CardShape
                            ) {
                                if (viewModel.isCheckingSms) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text("Check SMS")
                            }
                        }
                    }
                }
            }
        }

        // About Section
        SettingsSection(title = "SYSTEM") {
            SettingsCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    val nodeId = android.provider.Settings.Secure.getString(
                        context.contentResolver, 
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "Unknown"

                    InfoRow(icon = Icons.Outlined.Info, label = "Version", value = "1.0.0")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    InfoRow(icon = Icons.Outlined.Dns, label = "Node ID", value = nodeId.take(12).uppercase())
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
            SettingsCard {
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
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                "Logout",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "End current session",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = { showLogoutDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = DesignTokens.ButtonShape
                    ) {
                        Text("Logout")
                    }
                }
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
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
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = DesignTokens.CardShape,
        shadowElevation = 1.dp,
        tonalElevation = 1.dp,
        content = content
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.bounceClick { expanded = !expanded },
            shape = DesignTokens.ButtonShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(20.dp)
                        .scale(1f, if (expanded) -1f else 1f),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
        ) {
            content()
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
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PermissionGroupRow(
    group: PermissionGroup,
    refreshTrigger: Int
) {
    val context = LocalContext.current
    val isGranted = remember(group.permissions, refreshTrigger) {
        group.permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            group.icon,
            null,
            tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(group.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(group.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isGranted) {
            TextButton(onClick = { launcher.launch(group.permissions.toTypedArray()) }) {
                Text("Grant", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun BatteryOptimizationRow(refreshTrigger: Int) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    val isIgnoring = remember(refreshTrigger) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.BatterySaver,
            null,
            tint = if (isIgnoring) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("Battery Optimization", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                if (isIgnoring) "Optimized for background" else "Restricted by system",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isIgnoring) {
            TextButton(onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }) {
                Text("Fix", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
        }
    }
}

data class PermissionGroup(
    val label: String,
    val permissions: List<String>,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
