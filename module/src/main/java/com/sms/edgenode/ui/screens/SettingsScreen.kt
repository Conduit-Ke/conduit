package com.sms.edgenode.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sms.edgenode.ui.MainActivity
import com.sms.edgenode.ui.theme.ThemeState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit
) {
    var isDarkTheme by ThemeState.isDarkTheme
    var showLogoutDialog by remember { mutableStateOf(false) }
    
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
                description = "Required to route messages"
            ),
            PermissionGroup(
                label = "Phone State",
                permissions = listOf(Manifest.permission.READ_PHONE_STATE),
                description = "Read SIM status"
            ),
            PermissionGroup(
                label = "Notifications",
                permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                description = "Status updates"
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DarkMode, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Dark Theme")
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { isDarkTheme = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                permissionGroups.forEachIndexed { index, group ->
                    key(group.label, permissionRefreshTrigger) {
                        PermissionGroupRow(
                            group = group,
                            refreshTrigger = permissionRefreshTrigger
                        )
                    }
                    if (index < permissionGroups.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val context = LocalContext.current
        val app = context.applicationContext as com.sms.edgenode.EdgeNodeApp
        var preferredSim by remember { mutableIntStateOf(app.authService.getPreferredSimSlot()) }
        val activeSims = remember { com.sms.edgenode.sender.SimDetector.getActiveSimSlots(context) }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Advanced Routing",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // SIM Selection
                Text("Preferred SIM Slot", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Used for all outgoing messages regardless of receiving SIM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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
                            enabled = slot == -1 || activeSims.contains(slot)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Battery Optimization
                val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                val isIgnoringOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Battery Optimization", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (isIgnoringOptimizations) "Optimizations disabled (Recommended)" 
                            else "Optimizations enabled (Battery might kill service)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isIgnoringOptimizations) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    
                    if (!isIgnoringOptimizations) {
                        TextButton(onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }) {
                            Text("Change")
                        }
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                InfoRow("Version", "1.0.0")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                InfoRow("Build", "Production")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                InfoRow("Database", "Encrypted (SQLCipher)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Session",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout")
                }
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout Confirmation") },
                text = { Text("Are you sure you want to log out of ConduIT? You will need to authenticate again to manage the service.") },
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

data class PermissionGroup(
    val label: String,
    val permissions: List<String>,
    val description: String
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
    ) { _ ->
        // No need to manually update state; the local refreshTrigger or recomposition will handle it
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(group.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (isGranted) "Granted" else group.description,
                style = MaterialTheme.typography.labelSmall,
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        
        if (isGranted) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Button(
                onClick = { launcher.launch(group.permissions.toTypedArray()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
